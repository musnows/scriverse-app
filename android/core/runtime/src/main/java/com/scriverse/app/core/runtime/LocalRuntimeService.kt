package com.scriverse.app.core.runtime

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.scriverse.app.core.security.SecretRedactor
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class LocalRuntimeService : Service() {
    private val executor = Executors.newCachedThreadPool()
    private val status = AtomicReference(RuntimeStatus.STOPPED)
    private val process = AtomicReference<Process?>(null)

    private val binder = object : IRuntimeControl.Stub() {
        override fun startRuntime(entryScript: String, capability: String): Int =
            this@LocalRuntimeService.startRuntime(entryScript, capability)

        override fun stopRuntime() = this@LocalRuntimeService.stopRuntime()

        override fun getRuntimeStatus(): String = status.get().name

        override fun getPageSize(): Int = RuntimeBridge.nativePageSize()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        stopRuntime()
        executor.shutdownNow()
        super.onDestroy()
    }

    @Synchronized
    private fun startRuntime(entryScript: String, capability: String): Int {
        if (process.get()?.isAlive == true) return StartResult.ALREADY_RUNNING.code
        if (capability.length !in 32..256) return StartResult.INVALID_CAPABILITY.code

        val nodeBinary = File(applicationInfo.nativeLibraryDir, NODE_LIBRARY_NAME)
        val entry = File(entryScript)
        if (!nodeBinary.isFile || !entry.isFile || !entry.canonicalPath.startsWith(filesDir.canonicalPath)) {
            status.set(RuntimeStatus.UNAVAILABLE)
            return StartResult.RUNTIME_MISSING.code
        }

        status.set(RuntimeStatus.STARTING)
        return runCatching {
            val child = ProcessBuilder(
                nodeBinary.absolutePath,
                entry.absolutePath,
                "--host=127.0.0.1",
                "--port=0",
            )
                .directory(filesDir)
                .redirectErrorStream(true)
                .apply {
                    environment().clear()
                    environment()["HOME"] = filesDir.absolutePath
                    environment()["TMPDIR"] = cacheDir.absolutePath
                    environment()["LD_LIBRARY_PATH"] = applicationInfo.nativeLibraryDir
                    environment()["SCRIVERSE_RUNTIME_CAPABILITY"] = capability
                    environment()["NODE_ENV"] = "production"
                }
                .start()
            process.set(child)
            status.set(RuntimeStatus.RUNNING)
            executor.execute {
                runCatching {
                    child.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            android.util.Log.i(LOG_TAG, SecretRedactor.redact(line))
                        }
                    }
                }.onFailure { error ->
                    if (status.get() != RuntimeStatus.STOPPING) {
                        android.util.Log.w(LOG_TAG, SecretRedactor.redact(error.message ?: "Runtime log stream closed"))
                    }
                }
                val exitCode = runCatching { child.waitFor() }.getOrDefault(-1)
                process.compareAndSet(child, null)
                if (status.get() != RuntimeStatus.STOPPING) {
                    status.set(if (exitCode == 0) RuntimeStatus.STOPPED else RuntimeStatus.FAILED)
                }
            }
            StartResult.STARTED.code
        }.getOrElse { error ->
            android.util.Log.e(LOG_TAG, SecretRedactor.redact(error.message ?: "Runtime start failed"))
            status.set(RuntimeStatus.FAILED)
            StartResult.START_FAILED.code
        }
    }

    @Synchronized
    private fun stopRuntime() {
        val child = process.getAndSet(null) ?: run {
            status.set(RuntimeStatus.STOPPED)
            return
        }
        status.set(RuntimeStatus.STOPPING)
        child.destroy()
        if (!child.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            child.destroyForcibly()
            child.waitFor(STOP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        }
        status.set(RuntimeStatus.STOPPED)
    }

    private companion object {
        const val NODE_LIBRARY_NAME = "libnode.so"
        const val LOG_TAG = "ScriverseRuntime"
        const val STOP_TIMEOUT_SECONDS = 5L
    }
}

enum class RuntimeStatus { UNAVAILABLE, STOPPED, STARTING, RUNNING, STOPPING, FAILED }

enum class StartResult(val code: Int) {
    STARTED(0),
    ALREADY_RUNNING(1),
    RUNTIME_MISSING(2),
    INVALID_CAPABILITY(3),
    START_FAILED(4),
}

package com.scriverse.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.scriverse.app.core.runtime.IRuntimeControl
import com.scriverse.app.core.runtime.LocalRuntimeService
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RuntimeServiceTest {
    @Test
    fun startsNodeInPrivateRuntimeProcessAndReportsPageSize() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val entry = context.filesDir.resolve("runtime-test.mjs")
        entry.writeText("setInterval(() => {}, 1000);", Charsets.UTF_8)
        val connected = CountDownLatch(1)
        var control: IRuntimeControl? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                control = IRuntimeControl.Stub.asInterface(service)
                connected.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                control = null
            }
        }

        val intent = Intent(context, LocalRuntimeService::class.java)
        assertThat(context.bindService(intent, connection, Context.BIND_AUTO_CREATE)).isTrue()
        assertThat(connected.await(10, TimeUnit.SECONDS)).isTrue()
        val runtime = requireNotNull(control)
        try {
            assertThat(runtime.pageSize).isEqualTo(16_384)
            val result = runtime.startRuntime(entry.absolutePath, UUID.randomUUID().toString().replace("-", ""))
            assertThat(result).isAnyOf(0, 1)
            assertThat(runtime.runtimeStatus).isAnyOf("STARTING", "RUNNING")
        } finally {
            runtime.stopRuntime()
            context.unbindService(connection)
        }
    }
}

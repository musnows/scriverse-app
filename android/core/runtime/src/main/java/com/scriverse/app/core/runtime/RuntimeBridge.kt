package com.scriverse.app.core.runtime

object RuntimeBridge {
    init {
        System.loadLibrary("scriverse_runtime")
    }

    external fun nativePageSize(): Int
    external fun nativeLibraryProbe(): String
}

object PageSizePolicy {
    const val REQUIRED_ALIGNMENT = 16_384

    fun isSupported(pageSize: Int): Boolean =
        pageSize > 0 && pageSize % 4_096 == 0 && pageSize <= 65_536
}

package com.scriverse.app.core.runtime;

interface IRuntimeControl {
    int startRuntime(String entryScript, String capability);
    void stopRuntime();
    String getRuntimeStatus();
    int getPageSize();
}

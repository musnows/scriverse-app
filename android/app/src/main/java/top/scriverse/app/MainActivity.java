package top.scriverse.app;

import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NativeWorkspacePlugin.class);
        super.onCreate(savedInstanceState);
        bridge.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                if ("localhost".equals(request.getUrl().getHost()) && "GET".equals(request.getMethod())) {
                    NativeWorkspacePlugin plugin = (NativeWorkspacePlugin) bridge.getPlugin("NativeWorkspace").getInstance();
                    String path = request.getUrl().getEncodedPath();
                    if (request.getUrl().getEncodedQuery() != null) path += "?" + request.getUrl().getEncodedQuery();
                    WebResourceResponse response = plugin.interceptResource(path);
                    if (response != null) return response;
                }
                return super.shouldInterceptRequest(view, request);
            }
        });
    }
}

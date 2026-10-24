package top.scriverse.app;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import com.getcapacitor.BridgeActivity;
import com.getcapacitor.BridgeWebViewClient;

public class MainActivity extends BridgeActivity {
    private boolean graphPresentationActive;
    private int graphPreviousOrientation;
    private int graphPreviousBarsBehavior;
    private int graphPreviousVisibleBars;
    private boolean graphBarsRestoring;
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener graphBarRestorationObserver = this::checkGraphBarsRestored;

    private int visibleSystemBars() {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(getWindow().getDecorView());
        if (insets == null) return -1;
        int visible = 0;
        for (int type : new int[] { WindowInsetsCompat.Type.statusBars(), WindowInsetsCompat.Type.navigationBars(), WindowInsetsCompat.Type.captionBar() }) {
            if (insets.isVisible(type)) visible |= type;
        }
        return visible;
    }

    private void checkGraphBarsRestored() {
        if (!graphPresentationActive && graphBarsRestoring && visibleSystemBars() == graphPreviousVisibleBars) graphBarsRestoring = false;
    }

    void setGraphPresentation(boolean active) {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (active) {
            if (!graphPresentationActive) {
                graphPreviousOrientation = getRequestedOrientation();
                graphPreviousBarsBehavior = controller.getSystemBarsBehavior();
                if (!graphBarsRestoring) {
                    int visible = visibleSystemBars();
                    graphPreviousVisibleBars = visible < 0 ? WindowInsetsCompat.Type.systemBars() : visible;
                }
                graphBarsRestoring = false;
                graphPresentationActive = true;
                setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }
            hideGraphSystemBars();
        } else if (graphPresentationActive) {
            graphPresentationActive = false;
            graphBarsRestoring = true;
            setRequestedOrientation(graphPreviousOrientation);
            controller.setSystemBarsBehavior(graphPreviousBarsBehavior);
            if (graphPreviousVisibleBars != 0) controller.show(graphPreviousVisibleBars);
            int hidden = WindowInsetsCompat.Type.systemBars() & ~graphPreviousVisibleBars;
            if (hidden != 0) controller.hide(hidden);
        }
    }

    private void hideGraphSystemBars() {
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && graphPresentationActive) hideGraphSystemBars();
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        getWindow().getDecorView().post(() -> { if (graphPresentationActive) hideGraphSystemBars(); });
    }

    @Override
    public void onDestroy() {
        getWindow().getDecorView().getViewTreeObserver().removeOnGlobalLayoutListener(graphBarRestorationObserver);
        setGraphPresentation(false);
        super.onDestroy();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(NativeWorkspacePlugin.class);
        super.onCreate(savedInstanceState);
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(graphBarRestorationObserver);
        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                bridge.getWebView().evaluateJavascript("Boolean(window.scriverseMobileBack && window.scriverseMobileBack())", (handled) -> {
                    if (!"true".equals(handled)) {
                        if (graphPresentationActive) { setGraphPresentation(false); return; }
                        setEnabled(false);
                        getOnBackPressedDispatcher().onBackPressed();
                        setEnabled(true);
                    }
                });
            }
        });
        bridge.setWebViewClient(new BridgeWebViewClient(bridge) {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                setGraphPresentation(false);
                super.onPageStarted(view, url, favicon);
            }

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

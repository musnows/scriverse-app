package top.scriverse.app;

import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.BySelector;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;
import java.io.File;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MobileWorkflowTest {
    @org.junit.Rule public final org.junit.rules.TestWatcher failureCapture = new org.junit.rules.TestWatcher() {
        @Override protected void failed(Throwable error, org.junit.runner.Description description) {
            if (device == null || context == null) return;
            screenshot("android-failure");
            try { device.dumpWindowHierarchy(new File(context.getExternalFilesDir(null), "android-failure.xml")); } catch (Exception ignored) { System.out.println("Could not save the failed native hierarchy"); }
        }
    };
    private String packageName;
    private static final String MARKER = "Android offline persistence 20261023";
    private UiDevice device;
    private Context context;
    private String serverOrigin;
    private String llmOrigin;

    @Before public void openApp() throws Exception {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        packageName = context.getPackageName();
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Configurator.getInstance().setWaitForIdleTimeout(250);
        serverOrigin = InstrumentationRegistry.getArguments().getString("serverOrigin", "http://127.0.0.1:24400");
        llmOrigin = InstrumentationRegistry.getArguments().getString("llmOrigin", "http://127.0.0.1:24401");
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        assertNotNull(intent); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(intent);
        assertTrue("App did not become visible", device.wait(Until.hasObject(By.pkg(packageName)), 15000));
    }
    private UiObject2 find(BySelector selector) throws Exception {
        UiObject2 result = device.wait(Until.findObject(selector), 12000);
        if (result == null) { screenshot("android-failure"); device.dumpWindowHierarchy(new File(context.getExternalFilesDir(null), "android-failure.xml")); fail("Native control is missing: " + selector); }
        return result;
    }
    private void clickText(String value) throws Exception { find(By.text(value)).click(); }
    private void clickId(String id) throws Exception { find(By.res(id)).click(); }
    private void screenshot(String name) {
        device.waitForIdle(1200);
        android.os.SystemClock.sleep(500);
        assertTrue("Could not capture native screenshot", device.takeScreenshot(new File(context.getExternalFilesDir(null), name + ".png")));
    }
    private void hideKeyboard() { if (device.hasObject(By.pkg("com.google.android.inputmethod.latin"))) device.pressBack(); }
    private static int colorDifference(int first, int second) {
        return Math.max(Math.abs(android.graphics.Color.red(first) - android.graphics.Color.red(second)), Math.max(Math.abs(android.graphics.Color.green(first) - android.graphics.Color.green(second)), Math.abs(android.graphics.Color.blue(first) - android.graphics.Color.blue(second))));
    }
    private void assertCleanNativeFrame(String name) throws Exception {
        android.graphics.Bitmap screen = android.graphics.BitmapFactory.decodeFile(new File(context.getExternalFilesDir(null), name + ".png").getAbsolutePath());
        assertNotNull("Native screen capture is missing", screen);
        java.util.concurrent.atomic.AtomicReference<android.graphics.Bitmap> reference = new java.util.concurrent.atomic.AtomicReference<>();
        int[] location = new int[2];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (android.app.Activity activity : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                if (!(activity instanceof MainActivity)) continue;
                android.webkit.WebView web = ((MainActivity) activity).getBridge().getWebView();
                android.graphics.Bitmap frame = android.graphics.Bitmap.createBitmap(web.getWidth(), web.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
                web.getLocationOnScreen(location);
                web.draw(new android.graphics.Canvas(frame));
                reference.set(frame);
            }
        });
        android.graphics.Bitmap expected = reference.get();
        assertNotNull("The visible WebView is missing", expected);
        int samples = 0;
        int mismatches = 0;
        try {
            for (int y = 8; y < expected.getHeight() - 8; y += 8) {
                for (int x = 8; x < expected.getWidth() - 8; x += 8) {
                    int color = expected.getPixel(x, y);
                    if (colorDifference(color, expected.getPixel(x - 2, y)) > 1 || colorDifference(color, expected.getPixel(x + 2, y)) > 1 || colorDifference(color, expected.getPixel(x, y - 2)) > 1 || colorDifference(color, expected.getPixel(x, y + 2)) > 1) continue;
                    int screenX = x + location[0];
                    int screenY = y + location[1];
                    if (screenX < 0 || screenX >= screen.getWidth() || screenY < 0 || screenY >= screen.getHeight()) continue;
                    samples++;
                    if (colorDifference(color, screen.getPixel(screenX, screenY)) > 8) mismatches++;
                }
            }
            System.out.println("Native frame mismatch samples: " + mismatches + "/" + samples);
            assertTrue("Too few native screen samples", samples > 10000);
            assertTrue("Native screen contains stale compositor tiles: " + mismatches + "/" + samples, mismatches * 1000L <= samples * 3L);
        } finally { expected.recycle(); screen.recycle(); }
    }
    private void ensureLauncher() throws Exception {
        for (int step = 0; step < 5; step++) {
            if (device.hasObject(By.res("add-server"))) return;
            if (device.hasObject(By.res("mobile-connection-chip"))) {
                clickId("mobile-connection-chip"); clickText("切换工作区"); return;
            }
            if (device.hasObject(By.res("ai-panel-toggle"))) { clickId("ai-panel-toggle"); continue; }
            device.pressBack();
        }
        find(By.res("add-server"));
    }
    private void chooseBook() throws Exception {
        find(By.desc("打开 星海纪事")).click();
        find(By.res("mobile-bottom-nav"));
    }
    private void chooseFirstChapter() throws Exception {
        clickText("正文");
        find(By.text("正文目录"));
        UiObject2 chapter = device.findObject(By.textStartsWith("第一章 · 风起星港"));
        if (chapter == null) { find(By.textContains("第一卷 · 远航")).click(); }
        find(By.textStartsWith("第一章 · 风起星港")).click();
        find(By.res("chapter-content"));
    }
    private void configureProfile() throws Exception {
        ensureLauncher();
        if (!device.hasObject(By.text("Android Acceptance"))) {
            clickId("add-server");
            find(By.res("server-name")).setText("Android Acceptance");
            find(By.res("server-origin")).setText(serverOrigin);
            hideKeyboard(); clickText("保存并连接");
        } else clickText("进入工作区");
        UiObject2 download = device.wait(Until.findObject(By.text("立即离线全部")), 6000);
        if (download != null) download.click();
        UiObject2 tour = device.wait(Until.findObject(By.text("跳过导览")), 1500);
        if (tour != null) tour.click();
        find(By.res("mobile-connection-chip"));
        clickId("mobile-connection-chip");
        UiObject2 downloadOne = device.wait(Until.findObject(By.text("下载到本机")), 1000);
        if (downloadOne != null) downloadOne.click();
        assertTrue("Work package did not finish downloading", device.wait(Until.hasObject(By.textContains("1 部离线作品")), 45000));
        clickText("切换工作区");
        assertTrue("Offline entry is not enabled", find(By.text("离线进入")).isEnabled());
    }
    @Test public void prepareOfflineEdit() throws Exception {
        configureProfile();
        clickText("离线进入");
        find(By.desc("离线与同步：离线创作"));
        chooseBook(); chooseFirstChapter();
        clickId("chapter-edit-button");
        find(By.res("save-button"));
        String marker = MARKER + "-" + System.currentTimeMillis();
        assertTrue(context.getSharedPreferences("e2e-workflow", Context.MODE_PRIVATE).edit().putString("marker", marker).commit());
        find(By.res("chapter-content")).setText(marker);
        clickId("save-button");
        assertTrue("Offline change did not enter the upload queue", device.wait(Until.hasObject(By.desc("离线与同步：待同步 1")), 15000));
        screenshot("android-offline-editor");
    }
    private String savedMarker() {
        String marker = context.getSharedPreferences("e2e-workflow", Context.MODE_PRIVATE).getString("marker", null);
        assertNotNull("Offline preparation marker is missing", marker);
        return marker;
    }
    @Test public void verifyOfflinePersistence() throws Exception {
        ensureLauncher();
        clickText("离线进入"); chooseBook(); chooseFirstChapter();
        assertTrue("Offline text did not survive process restart", device.wait(Until.hasObject(By.res("chapter-content").textContains(savedMarker())), 15000));
        screenshot("android-offline-restored");
    }
    @Test public void verifyPersistenceAndSync() throws Exception {
        verifyOfflinePersistence();
        ensureLauncher(); clickText("进入工作区");
        assertTrue("Queued upload did not finish", device.wait(Until.hasObject(By.desc("离线与同步：在线")), 20000));
        chooseBook(); chooseFirstChapter();
        assertTrue("Uploaded text is not visible online", device.wait(Until.hasObject(By.res("chapter-content").textContains(savedMarker())), 15000));
        screenshot("android-online-synced");
        ensureLauncher();
    }
    private void fillVisibleEdit(int index, String value) throws Exception {
        List<UiObject2> fields = device.findObjects(By.clazz("android.widget.EditText"));
        assertTrue("Expected input field is missing", fields.size() > index);
        fields.get(index).setText(value);
    }
    private UiObject2 scrollToText(String value) throws Exception {
        for (int step = 0; step < 6; step++) {
            UiObject2 result = device.findObject(By.text(value));
            if (result != null && !result.getVisibleBounds().isEmpty()) return result;
            device.swipe(device.getDisplayWidth() / 2, device.getDisplayHeight() * 4 / 5, device.getDisplayWidth() / 2, device.getDisplayHeight() / 3, 30);
        }
        return find(By.text(value));
    }
    @Test public void configurePhoneModel() throws Exception {
        ensureLauncher(); clickId("local-ai-open");
        find(By.res("local-ai-new-provider"));
        if (!device.hasObject(By.text("Phone Endpoint"))) {
            clickId("local-ai-new-provider");
            find(By.res("local-ai-form-dialog"));
            fillVisibleEdit(0, "Phone Endpoint");
            fillVisibleEdit(1, llmOrigin + "/v1");
            fillVisibleEdit(2, "native-e2e-only-api-key");
            hideKeyboard(); clickId("local-ai-dialog-submit");
        }
        scrollToText("添加模型");
        if (!device.hasObject(By.descStartsWith("编辑模型 Phone Writing Model"))) {
            clickText("添加模型");
            find(By.res("local-ai-form-dialog"));
            fillVisibleEdit(0, "Phone Writing Model");
            fillVisibleEdit(1, "mobile-test-model");
            hideKeyboard(); clickId("local-ai-dialog-submit");
        }
        UiObject2 test = scrollToText("测试连接");
        assertNotNull("Provider connection test is missing", test); test.click();
        assertTrue("Phone endpoint connection test failed", device.wait(Until.hasObject(By.textContains("连接正常")), 15000));
        screenshot("android-local-ai-config");
        device.pressBack();
    }
    @Test public void offlinePhoneModelChat() throws Exception {
        ensureLauncher(); clickText("离线进入"); chooseBook();
        clickText("AI"); find(By.res("ai-prompt")).setText("OFFLINE_TOOL_ACCEPTANCE Describe the downloaded story settings in one sentence.");
        clickId("ai-send");
        assertTrue("The phone model did not answer in offline mode", device.wait(Until.hasObject(By.textContains("本机模型已读取离线副本")), 30000));
        hideKeyboard();
        java.util.concurrent.CountDownLatch observed = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<String> layout = new java.util.concurrent.atomic.AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (android.app.Activity activity : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                if (activity instanceof MainActivity) {
                    android.webkit.WebView web = ((MainActivity) activity).getBridge().getWebView();
                    web.evaluateJavascript("JSON.stringify({ overflow: document.documentElement.scrollWidth>innerWidth, panel: document.querySelector('.ai-panel').getBoundingClientRect().toJSON(), viewport: [innerWidth,innerHeight], fontLoaded: document.fonts.check('16px \\\"Scriverse Mono\\\"') })", value -> { layout.set(value); observed.countDown(); });
                }
            }
        });
        assertTrue(observed.await(5, java.util.concurrent.TimeUnit.SECONDS));
        org.json.JSONObject dimensions = new org.json.JSONObject((String) new org.json.JSONTokener(layout.get()).nextValue());
        assertFalse("Native AI workspace overflows horizontally", dimensions.getBoolean("overflow"));
        assertTrue("Mobile monospace font did not load", dimensions.getBoolean("fontLoaded"));
        assertTrue("AI workspace extends below the screen", dimensions.getJSONObject("panel").getDouble("bottom") <= dimensions.getJSONArray("viewport").getDouble(1) + 1);
        screenshot("android-offline-ai-chat");
        assertCleanNativeFrame("android-offline-ai-chat");
        device.pressBack();
        find(By.res("mobile-connection-chip"));
        java.util.concurrent.atomic.AtomicInteger restoredLayer = new java.util.concurrent.atomic.AtomicInteger(-1);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (android.app.Activity activity : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                if (activity instanceof MainActivity) restoredLayer.set(((MainActivity) activity).getBridge().getWebView().getLayerType());
            }
        });
        assertEquals("Leaving AI must restore normal workspace rendering", android.view.View.LAYER_TYPE_NONE, restoredLayer.get());
    }
    @Test public void libraryNavigationAndNativeBack() throws Exception {
        ensureLauncher(); clickText("离线进入"); chooseBook();
        clickText("更多");
        assertFalse("Offline commands must not expose the unavailable messaging workspace", device.hasObject(By.text("消息与协作")));
        device.pressBack();
        clickText("设定库"); find(By.text("角色"));
        device.pressBack(); assertTrue("Native back did not close the navigation sheet", device.wait(Until.gone(By.res("mobile-command-sheet")), 5000));
        clickText("设定库"); clickText("角色");
        find(By.text("角色与人物属性"));
        screenshot("android-characters");
        assertTrue("Character filters must start collapsed", !device.hasObject(By.res("character-filter-panel")));
    }
}

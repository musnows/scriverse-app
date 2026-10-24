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
        UiObject2 result = device.wait(Until.findObject(selector.pkg(packageName)), 12000);
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
        find(By.res(java.util.regex.Pattern.compile("add-server|mobile-bottom-nav|ai-panel-toggle|local-ai-new-provider|app-settings-dialog")));
        for (int step = 0; step < 5; step++) {
            assertTrue("The isolated app is no longer visible", device.wait(Until.hasObject(By.pkg(packageName)), 5000));
            if (device.hasObject(By.res("app-settings-dialog").pkg(packageName))) { device.pressBack(); device.wait(Until.gone(By.res("app-settings-dialog").pkg(packageName)), 5000); continue; }
            if (device.hasObject(By.res("add-server").pkg(packageName))) return;
            if (device.hasObject(By.res("mobile-bottom-nav").pkg(packageName))) {
                clickText("更多"); scrollTo(By.textStartsWith("切换工作区")).click(); find(By.res("add-server")); return;
            }
            if (device.hasObject(By.res("ai-panel-toggle").pkg(packageName))) { clickId("ai-panel-toggle"); find(By.res("mobile-bottom-nav")); continue; }
            if (device.hasObject(By.res("local-ai-new-provider").pkg(packageName))) { clickText("返回工作区"); continue; }
            find(By.res("mobile-bottom-nav"));
        }
        find(By.res("add-server"));
    }
    private void chooseBook() throws Exception {
        find(By.desc("打开 星海纪事")).click();
        find(By.res("mobile-bottom-nav"));
        assertTrue("Workspace navigation did not become ready", device.wait(Until.hasObject(By.text("正文").enabled(true)), 15000));
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
        find(By.res("mobile-bottom-nav"));
        clickText("更多"); clickId("mobile-connection-chip");
        UiObject2 downloadOne = device.wait(Until.findObject(By.text("下载到本机")), 1000);
        if (downloadOne != null) downloadOne.click();
        assertTrue("Work package did not finish downloading", device.wait(Until.hasObject(By.textContains("1 部离线作品")), 45000));
        clickText("切换工作区");
        assertTrue("Offline entry is not enabled", find(By.text("离线进入")).isEnabled());
    }
    @Test public void prepareOfflineEdit() throws Exception {
        configureProfile();
        clickText("离线进入");
        waitForSyncState("离线创作");
        chooseBook(); chooseFirstChapter();
        clickId("chapter-edit-button");
        find(By.res("save-button"));
        String marker = MARKER + "-" + System.currentTimeMillis();
        assertTrue(context.getSharedPreferences("e2e-workflow", Context.MODE_PRIVATE).edit().putString("marker", marker).commit());
        find(By.res("chapter-content")).setText(marker);
        clickId("save-button");
        assertTrue("Offline save did not finish", device.wait(Until.hasObject(By.textContains("正文已保存到本机")), 15000));
        assertFalse("Save notifications must not cover the bottom navigation", fontMetrics().getBoolean("toastCoversNavigation"));
        waitForSyncState("待同步 1");
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
        waitForSyncState("在线");
        chooseBook(); chooseFirstChapter();
        assertTrue("Uploaded text is not visible online", device.wait(Until.hasObject(By.res("chapter-content").textContains(savedMarker())), 15000));
        screenshot("android-online-synced");
        ensureLauncher();
    }
    private void fillVisibleEdit(int index, String value) throws Exception {
        List<UiObject2> fields = device.findObjects(By.clazz("android.widget.EditText").pkg(packageName));
        assertTrue("Expected input field is missing", fields.size() > index);
        fields.get(index).setText(value);
    }
    private UiObject2 scrollToText(String value) throws Exception {
        return scrollTo(By.text(value));
    }
    private UiObject2 scrollTo(BySelector selector) throws Exception {
        BySelector scoped = selector.pkg(packageName);
        for (int step = 0; step < 6; step++) {
            UiObject2 result = device.findObject(scoped);
            if (result != null && !result.getVisibleBounds().isEmpty()) return result;
            device.swipe(device.getDisplayWidth() / 2, device.getDisplayHeight() * 4 / 5, device.getDisplayWidth() / 2, device.getDisplayHeight() / 3, 30);
        }
        UiObject2 result = device.wait(Until.findObject(scoped), 12000);
        assertNotNull("Native scrolling control is missing: " + scoped, result);
        return result;
    }
    @Test public void configurePhoneModel() throws Exception {
        ensureLauncher(); clickId("app-more-button"); clickId("local-ai-open");
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
        find(By.res("mobile-bottom-nav"));
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

    @Test public void welcomeDirectoryShortcut() throws Exception {
        ensureLauncher(); clickText("离线进入"); chooseBook();
        assertTrue("The welcome directory shortcut must work offline", find(By.res("welcome-directory-button")).isEnabled());
        Rect create = find(By.res("welcome-new-work")).getVisibleBounds();
        Rect browse = find(By.res("welcome-directory-button")).getVisibleBounds();
        assertTrue("The directory shortcut must be to the right of new chapter", browse.left >= create.right);
        assertEquals("Welcome actions must align on one row", create.top, browse.top);
        assertMobileLayout();
        screenshot("android-welcome-directory");
        clickId("welcome-directory-button"); find(By.text("正文目录"));
        screenshot("android-welcome-directory-open");
        device.pressBack();
        assertTrue("Native back did not close the directory", device.wait(Until.gone(By.res("mobile-command-sheet").pkg(packageName)), 5000));
        clickId("welcome-directory-button"); find(By.text("正文目录"));
        if (!device.hasObject(By.textStartsWith("第一章 · 风起星港").pkg(packageName))) find(By.textContains("第一卷 · 远航")).click();
        find(By.textStartsWith("第一章 · 风起星港")).click(); find(By.res("chapter-content"));
        assertTrue("Selecting a chapter must close the directory", device.wait(Until.gone(By.res("mobile-command-sheet").pkg(packageName)), 5000));
        chooseFirstChapter();
        clickText("书架"); find(By.desc("打开 星海纪事"));
        assertFalse("The shortcut must not appear outside a work", device.hasObject(By.res("welcome-directory-button").pkg(packageName)));
    }

    private org.json.JSONObject fontMetrics() throws Exception {
        java.util.concurrent.CountDownLatch observed = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<String> result = new java.util.concurrent.atomic.AtomicReference<>();
        String inspect = "JSON.stringify({ scale: Number(document.documentElement.dataset.appFontSize), overflow: document.documentElement.scrollWidth > innerWidth, "
            + "presetCount: document.querySelectorAll('#app-font-size input[type=radio]').length, checkedPresetCount: document.querySelectorAll('#app-font-size input:checked').length, selectedPreset: document.querySelector('#app-font-size input:checked')?.value, percentageControls: Boolean(document.querySelector('#app-font-size input[type=range]')) || /[%％]/u.test(document.querySelector('.app-font-settings')?.textContent ?? ''), "
            + "samples: Object.fromEntries(['#server-list-title','.app-font-preview p','.module-header h1','#chapter-content','.vditor-reset','#ai-prompt'].map(selector => { const node = document.querySelector(selector); return [selector, node ? parseFloat(getComputedStyle(node).fontSize) : null]; })), "
            + "editorBase: parseFloat(getComputedStyle(document.documentElement).getPropertyValue('--editor-font-size')), "
            + "toastCoversNavigation: (()=>{const nav=document.querySelector('#mobile-bottom-nav')?.getBoundingClientRect();const toast=document.querySelector('#toast-region')?.getBoundingClientRect();return Boolean(nav?.height>0&&toast?.height>0&&toast.bottom>nav.top+1&&toast.top<nav.bottom);})(), "
            + "persistentBrand: [...document.querySelectorAll('.topbar .brand-block,.auth-brand,.launcher-brand > img,.mobile-local-ai .module-header .eyebrow')].some(node => node.getBoundingClientRect().height > 0), "
            + "outsideButtons: [...document.querySelectorAll('.module-header-actions button,.module-header-actions a,.editor-actions button,.entity-editor-mode-actions button,.character-editor-header-actions button,.ai-heading button,.card-actions button,.app-font-option,.mobile-welcome-actions button')].filter(node => { const rect=node.getBoundingClientRect(); return rect.width>0 && rect.height>0 && getComputedStyle(node).visibility!=='hidden' && (rect.left < -1 || rect.right > innerWidth+1); }).map(node=>node.id || node.getAttribute('aria-label') || node.textContent.trim().slice(0,40)) })";
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (android.app.Activity activity : androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)) {
                if (!(activity instanceof MainActivity)) continue;
                ((MainActivity) activity).getBridge().getWebView().evaluateJavascript(inspect, value -> { result.set(value); observed.countDown(); });
            }
        });
        assertTrue("Could not inspect native typography", observed.await(5, java.util.concurrent.TimeUnit.SECONDS));
        return new org.json.JSONObject((String) new org.json.JSONTokener(result.get()).nextValue());
    }

    private void assertMobileLayout() throws Exception {
        org.json.JSONObject layout = fontMetrics();
        assertFalse("Mobile layout overflows horizontally", layout.getBoolean("overflow"));
        assertFalse("Branding must only appear inside More", layout.getBoolean("persistentBrand"));
        assertEquals("Mobile actions extend outside the screen: " + layout.getJSONArray("outsideButtons"), 0, layout.getJSONArray("outsideButtons").length());
    }

    private void largerFont() throws Exception {
        clickId("app-font-size-120");
        assertTrue(device.wait(Until.hasObject(By.res("app-font-size-value").text("超大")), 5000));
        assertEquals("The selected font preset is not checked", 120, fontMetrics().getInt("selectedPreset"));
    }

    private void workspaceFontSettings() throws Exception { clickText("更多"); find(By.textStartsWith("显示与字号")).click(); find(By.res("app-font-size")); }

    private void waitForSyncState(String value) throws Exception {
        find(By.res("mobile-bottom-nav")); clickText("更多");
        find(By.res("mobile-command-sheet"));
        assertTrue("Unexpected native synchronization state: " + value, device.wait(Until.hasObject(By.desc("离线与同步：" + value)), 20000));
        device.pressBack();
    }

    @Test public void globalFontSizingAcrossPages() throws Exception {
        ensureLauncher(); clickId("app-more-button"); clickId("app-font-reset");
        org.json.JSONObject small = fontMetrics();
        assertEquals(90, small.getInt("scale"));
        assertEquals(14.4, small.getJSONObject("samples").getDouble(".app-font-preview p"), .15);
        assertEquals("Font choices must be fixed presets", 5, small.getInt("presetCount"));
        assertFalse("Font settings must not expose percentage controls", small.getBoolean("percentageControls"));
        for (int value : new int[] { 80, 90, 100, 110, 120 }) {
            clickId("app-font-size-" + value);
            org.json.JSONObject selected = fontMetrics();
            assertEquals(value, selected.getInt("scale"));
            assertEquals("Font presets must have a single selection", 1, selected.getInt("checkedPresetCount"));
            assertEquals("The requested preset is not checked", value, selected.getInt("selectedPreset"));
            assertMobileLayout();
        }
        org.json.JSONObject large = fontMetrics();
        assertMobileLayout();
        assertEquals(small.getJSONObject("samples").getDouble("#server-list-title") * 4 / 3, large.getJSONObject("samples").getDouble("#server-list-title"), .15);
        assertEquals(19.2, large.getJSONObject("samples").getDouble(".app-font-preview p"), .15);
        clickId("local-ai-open"); find(By.res("local-ai-new-provider"));
        assertEquals(120, fontMetrics().getInt("scale"));
        clickId("app-more-button"); clickId("app-font-reset"); device.pressBack();
        double localSmall = fontMetrics().getJSONObject("samples").getDouble(".module-header h1");
        clickId("app-more-button"); largerFont(); device.pressBack();
        assertEquals(localSmall * 4 / 3, fontMetrics().getJSONObject("samples").getDouble(".module-header h1"), .15);
        assertMobileLayout();
        clickText("返回工作区"); find(By.res("add-server"));
        clickText("离线进入"); chooseBook();
        clickText("设定库"); clickText("角色"); find(By.text("角色与人物属性"));
        workspaceFontSettings(); clickId("app-font-reset"); device.pressBack();
        double moduleSmall = fontMetrics().getJSONObject("samples").getDouble(".module-header h1");
        workspaceFontSettings(); largerFont(); device.pressBack();
        assertEquals(moduleSmall * 4 / 3, fontMetrics().getJSONObject("samples").getDouble(".module-header h1"), .15);
        assertMobileLayout();
        chooseFirstChapter();
        org.json.JSONObject prose = fontMetrics();
        assertEquals(prose.getDouble("editorBase") * 1.2, prose.getJSONObject("samples").getDouble("#chapter-content"), .15);
        assertMobileLayout();
        clickText("设定库"); clickText("设定");
        Rect settingCard = find(By.desc("查看设定 潮汐引擎")).getVisibleBounds();
        device.click(settingCard.left + 32, settingCard.top + 32); clickId("setting-editor-edit");
        find(By.textContains("冷却时间为三小时"));
        assertEquals(19.2, fontMetrics().getJSONObject("samples").getDouble(".vditor-reset"), .15);
        assertMobileLayout();
        screenshot("android-font-markdown-large");
        clickId("setting-editor-back");
        clickText("AI"); find(By.res("ai-prompt"));
        assertEquals(19.2, fontMetrics().getJSONObject("samples").getDouble("#ai-prompt"), .15);
        assertMobileLayout();
        clickId("ai-panel-toggle"); find(By.res("mobile-bottom-nav"));
        ensureLauncher(); clickId("app-more-button");
        assertEquals(120, fontMetrics().getInt("scale"));
        screenshot("android-font-settings-large");
    }

    @Test public void globalFontSizeSurvivesRestart() throws Exception {
        ensureLauncher(); clickId("app-more-button");
        assertEquals("Global typography did not survive process restart", 120, fontMetrics().getInt("scale"));
        assertEquals("The saved preset is not selected after restart", 120, fontMetrics().getInt("selectedPreset"));
        clickId("app-font-reset");
        assertEquals(90, fontMetrics().getInt("scale"));
        assertEquals("Reset must select the standard preset", 90, fontMetrics().getInt("selectedPreset"));
        screenshot("android-font-settings-default");
        device.pressBack(); find(By.res("add-server"));
    }
}

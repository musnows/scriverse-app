package top.scriverse.app;

import static org.junit.Assert.*;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.SystemClock;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.Configurator;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class GraphPresentationTest {
    private MainActivity activity;
    private UiDevice device;
    private int originalOrientation;

    private <T> T ui(Supplier<T> action) {
        AtomicReference<T> result = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> result.set(action.get()));
        return result.get();
    }

    private void await(String message, BooleanSupplier condition) {
        long deadline = SystemClock.elapsedRealtime() + 10000;
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition.getAsBoolean()) return;
            SystemClock.sleep(50);
        }
        fail(message);
    }

    private int visibleBars() {
        return ui(() -> {
            WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(activity.getWindow().getDecorView());
            if (insets == null) return -1;
            int visible = 0;
            for (int type : new int[] { WindowInsetsCompat.Type.statusBars(), WindowInsetsCompat.Type.navigationBars() }) {
                if (insets.isVisible(type)) visible |= type;
            }
            return visible;
        });
    }

    @Before public void openApp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        Configurator.getInstance().setWaitForIdleTimeout(250);
        android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        assertNotNull(intent);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
        assertTrue("The isolated launcher did not become visible", device.wait(Until.hasObject(By.res("add-server").pkg(context.getPackageName())), 15000));
        activity = ui(() -> ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).stream().filter(MainActivity.class::isInstance).map(MainActivity.class::cast).findFirst().orElse(null));
        assertNotNull(activity);
        originalOrientation = ui(activity::getRequestedOrientation);
        ui(() -> { activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); return null; });
        await("Portrait preparation failed", () -> device.getDisplayHeight() > device.getDisplayWidth());
        await("System bar insets are unavailable", () -> visibleBars() >= 0);
    }

    @After public void restoreApp() {
        if (activity == null) return;
        ui(() -> { activity.setGraphPresentation(false); activity.setRequestedOrientation(originalOrientation); return null; });
    }

    @Test public void landscapePresentationRestoresOrientationAndSystemBars() {
        int before = visibleBars();
        int behavior = ui(() -> WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView()).getSystemBarsBehavior());
        ui(() -> { activity.setGraphPresentation(true); return null; });
        await("The activity did not enter landscape", () -> device.getDisplayWidth() > device.getDisplayHeight());
        await("Immersive graph presentation did not hide system bars", () -> visibleBars() == 0);
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE, (int) ui(activity::getRequestedOrientation));
        ui(() -> { activity.setGraphPresentation(true); activity.setGraphPresentation(false); return null; });
        await("The original orientation was not restored", () -> device.getDisplayHeight() > device.getDisplayWidth());
        await("The original system bars were not restored", () -> visibleBars() == before);
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, (int) ui(activity::getRequestedOrientation));
        assertEquals(behavior, (int) ui(() -> WindowCompat.getInsetsController(activity.getWindow(), activity.getWindow().getDecorView()).getSystemBarsBehavior()));
    }

    @Test public void navigationLeavesLandscapePresentation() {
        ui(() -> { activity.setGraphPresentation(true); return null; });
        await("Landscape preparation failed", () -> device.getDisplayWidth() > device.getDisplayHeight());
        ui(() -> { activity.getBridge().getWebView().reload(); return null; });
        await("Reload must restore orientation", () -> ui(activity::getRequestedOrientation) == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        await("Reload must leave landscape", () -> device.getDisplayHeight() > device.getDisplayWidth());
    }

    @Test public void nativeBackLeavesOrphanedPresentation() {
        ui(() -> { activity.setGraphPresentation(true); return null; });
        await("Landscape preparation failed", () -> device.getDisplayWidth() > device.getDisplayHeight());
        device.pressBack();
        await("Back must restore orientation", () -> ui(activity::getRequestedOrientation) == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        await("Back must leave landscape", () -> device.getDisplayHeight() > device.getDisplayWidth());
    }
}

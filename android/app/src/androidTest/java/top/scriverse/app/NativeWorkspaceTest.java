package top.scriverse.app;

import static org.junit.Assert.*;
import android.content.Context;
import android.util.Base64;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class NativeWorkspaceTest {
    private TestPlugin plugin;
    private String profileId;
    private String origin;
    private String authOrigin;
    private static class TestPlugin extends NativeWorkspacePlugin {
        final java.util.List<JSObject> streamEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        final java.util.List<JSObject> uploadEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        final CountDownLatch streamDone = new CountDownLatch(1);
        @Override public Context getContext() { return InstrumentationRegistry.getInstrumentation().getTargetContext(); }
        @Override protected void notifyListeners(String eventName, JSObject value) {
            if (eventName.equals("httpUploadProgress")) uploadEvents.add(value);
            if (eventName.equals("httpStream")) {
                streamEvents.add(value);
                if (value.optBoolean("done") || value.has("error")) streamDone.countDown();
            }
        }
    }
    private static class Call extends PluginCall {
        final CountDownLatch done = new CountDownLatch(1);
        JSObject result;
        String errorCode;
        Call(JSObject data) { super(null, "NativeWorkspace", UUID.randomUUID().toString(), "test", data); }
        @Override public void resolve(JSObject data) { result = data; done.countDown(); }
        @Override public void resolve() { resolve(new JSObject()); }
        @Override public void reject(String message, String code) { errorCode = code; done.countDown(); }
        void await() throws Exception { assertTrue("Native request timed out", done.await(25, TimeUnit.SECONDS)); }
    }
    private Call configure(String origin, boolean offline) throws Exception {
        JSObject data = new JSObject(); data.put("profileId", profileId); data.put("origin", origin); data.put("clientVersion", "1.2.2"); data.put("offline", offline);
        Call call = new Call(data); plugin.configure(call); call.await(); assertNull(call.errorCode); return call;
    }
    private void writeSecret(String name, String value) throws Exception {
        Method method = NativeWorkspacePlugin.class.getDeclaredMethod("writeSecret", String.class, String.class); method.setAccessible(true); method.invoke(plugin, name, value);
    }
    private String readSecret(String name) throws Exception {
        Method method = NativeWorkspacePlugin.class.getDeclaredMethod("readSecret", String.class); method.setAccessible(true); return (String) method.invoke(plugin, name);
    }
    private Call request(String path, String method, String body) throws Exception {
        JSObject data = new JSObject(); data.put("requestId", UUID.randomUUID().toString()); data.put("url", origin + path); data.put("method", method); data.put("body", Base64.encodeToString(body.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        JSObject headers = new JSObject(); headers.put("Content-Type", "application/json"); data.put("headers", headers);
        Call call = new Call(data); plugin.request(call); call.await(); return call;
    }
    @Before public void initialize() {
        plugin = new TestPlugin(); plugin.load(); profileId = UUID.randomUUID().toString();
        origin = InstrumentationRegistry.getArguments().getString("serverOrigin", "http://127.0.0.1:24400");
        authOrigin = InstrumentationRegistry.getArguments().getString("authOrigin", "http://127.0.0.1:24500");
    }
    @Test public void graphPresentationRejectsInvalidStates() throws Exception {
        for (JSObject input : new JSObject[] { new JSObject(), new JSObject().put("active", "true"), new JSObject().put("active", 1) }) {
            Call call = new Call(input); plugin.setGraphPresentation(call); call.await();
            assertEquals("GRAPH_PRESENTATION_INVALID", call.errorCode);
        }
    }
    @Test public void secretsAreEncryptedAndBoundToTheirNames() throws Exception {
        String id = UUID.randomUUID().toString(); String key = "ai:" + id;
        writeSecret(key, "isolated-test-api-key");
        assertEquals("isolated-test-api-key", readSecret(key));
        String ciphertext = plugin.getContext().getSharedPreferences("workspace", Context.MODE_PRIVATE).getString(key, "");
        assertFalse(ciphertext.contains("isolated-test-api-key"));
        String other = "ai:" + UUID.randomUUID();
        plugin.getContext().getSharedPreferences("workspace", Context.MODE_PRIVATE).edit().putString(other, ciphertext).commit();
        try { readSecret(other); fail("Credential AAD must prevent cross-name decryption"); } catch (java.lang.reflect.InvocationTargetException expected) { assertNotNull(expected.getCause()); }
    }
    @Test public void changingOriginsDoesNotReuseTokensOrUsers() throws Exception {
        JSONObject saved = new JSONObject(); saved.put("origin", "https://different.example"); saved.put("token", "isolated-old-origin-token"); saved.put("user", new JSONObject().put("userId", UUID.randomUUID().toString()));
        writeSecret("session:" + profileId, saved.toString());
        Call configured = configure(origin, false);
        assertTrue(configured.result.isNull("user") || !configured.result.has("user"));
        Call response = request("/api/__e2e/authorization", "GET", ""); assertNull(response.errorCode);
        JSONObject body = new JSONObject(new String(Base64.decode(response.result.getString("body"), Base64.NO_WRAP), StandardCharsets.UTF_8));
        assertFalse(body.getBoolean("authorizationPresent"));
    }
    @Test public void offlineModeBlocksServerButAllowsThePhoneModelEndpoint() throws Exception {
        configure(origin, true);
        assertEquals("NETWORK_REQUEST_FAILED", request("/api/health", "GET", "").errorCode);
        Call model = request("/v1/chat/completions", "POST", "{\"model\":\"mobile-test-model\",\"messages\":[{\"role\":\"user\",\"content\":\"test\"}]}");
        assertNull(model.errorCode); assertEquals(200, model.result.getInt("status"));
    }
    private Call authRequest(String path, String method, String body) throws Exception {
        JSObject data = new JSObject(); data.put("requestId", UUID.randomUUID().toString()); data.put("url", authOrigin + path); data.put("method", method); data.put("body", Base64.encodeToString(body.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
        JSObject headers = new JSObject(); headers.put("Content-Type", "application/json"); data.put("headers", headers);
        Call call = new Call(data); plugin.request(call); call.await(); assertNull(call.errorCode); return call;
    }
    private JSONObject payload(Call call) throws Exception { return new JSONObject(new String(Base64.decode(call.result.getString("body"), Base64.NO_WRAP), StandardCharsets.UTF_8)); }
    @Test public void nativeLoginUsesBearerSessionsAndNeverReturnsTheTokenToThePage() throws Exception {
        configure(authOrigin, false);
        JSONObject captcha = payload(authRequest("/api/auth/captcha", "GET", "")).getJSONObject("data");
        JSONObject input = new JSONObject(); input.put("username", "native-auth"); input.put("password", "native-auth-only-password"); input.put("captchaId", captcha.getString("captchaId")); input.put("captchaAnswer", captcha.getString("answer"));
        Call login = authRequest("/api/auth/login", "POST", input.toString());
        assertEquals(200, login.result.getInt("status"));
        assertFalse(payload(login).getJSONObject("data").has("token"));
        assertFalse(login.result.getJSONObject("headers").has("set-cookie"));
        assertTrue(payload(authRequest("/api/auth/session", "GET", "")).getJSONObject("data").getBoolean("authenticated"));
        assertEquals(200, authRequest("/api/works", "GET", "").result.getInt("status"));
        Call switched = configure(authOrigin.replace("127.0.0.1", "localhost"), false);
        assertTrue(switched.result.isNull("user") || !switched.result.has("user"));
        configure(authOrigin, false);
        assertTrue(payload(authRequest("/api/auth/session", "GET", "")).getJSONObject("data").getBoolean("authenticated"));
        assertEquals(204, authRequest("/api/auth/session", "DELETE", "").result.getInt("status"));
        assertFalse(payload(authRequest("/api/auth/session", "GET", "")).getJSONObject("data").getBoolean("authenticated"));
    }
    @Test public void unsafeProtocolsCannotBeRead() throws Exception {
        JSObject data = new JSObject(); data.put("requestId", UUID.randomUUID().toString()); data.put("url", "file:///data/local/tmp/secret");
        Call call = new Call(data); plugin.request(call); call.await(); assertEquals("NETWORK_REQUEST_FAILED", call.errorCode);
    }
    @Test public void nativeStreamingPreservesUnicodeAndCompletionEvents() throws Exception {
        configure(origin, false);
        JSObject data = new JSObject(); data.put("requestId", UUID.randomUUID().toString()); data.put("url", origin + "/api/__e2e/stream"); data.put("stream", true);
        Call call = new Call(data); plugin.request(call); call.await(); assertNull(call.errorCode); assertEquals(200, call.result.getInt("status"));
        assertTrue("Native stream did not finish", plugin.streamDone.await(10, TimeUnit.SECONDS));
        java.io.ByteArrayOutputStream content = new java.io.ByteArrayOutputStream();
        for (JSObject event : plugin.streamEvents) {
            assertFalse("Native stream reported a transport error", event.has("error"));
            if (event.has("chunk")) content.write(Base64.decode(event.getString("chunk"), Base64.NO_WRAP));
        }
        assertEquals("event: native-message\ndata: 中文流式内容\n\ndata: [DONE]\n\n", content.toString("UTF-8"));
        assertTrue(plugin.streamEvents.get(plugin.streamEvents.size() - 1).getBoolean("done"));
    }
    @Test public void nativeMultipartUploadsPreserveBytesAndReportProgress() throws Exception {
        configure(origin, false);
        String body = "--native-e2e-boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"story.txt\"\r\nContent-Type: text/plain\r\n\r\n中文上传\r\n--native-e2e-boundary--\r\n";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        JSObject data = new JSObject(); data.put("requestId", UUID.randomUUID().toString()); data.put("url", origin + "/api/__e2e/multipart"); data.put("method", "POST"); data.put("body", Base64.encodeToString(bytes, Base64.NO_WRAP));
        JSObject headers = new JSObject(); headers.put("Content-Type", "multipart/form-data; boundary=native-e2e-boundary"); data.put("headers", headers);
        Call call = new Call(data); plugin.request(call); call.await(); assertNull(call.errorCode); assertEquals(200, call.result.getInt("status"));
        JSONObject remote = payload(call); assertEquals(bytes.length, remote.getInt("size")); assertTrue(remote.getBoolean("multipart")); assertTrue(remote.getBoolean("unicodePreserved"));
        assertFalse("Native upload did not report progress", plugin.uploadEvents.isEmpty());
        assertEquals(bytes.length, plugin.uploadEvents.get(plugin.uploadEvents.size() - 1).getInt("loaded"));
    }
}

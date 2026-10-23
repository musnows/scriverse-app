package top.scriverse.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.webkit.WebResourceResponse;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.json.JSONObject;

@CapacitorPlugin(name = "NativeWorkspace")
public class NativeWorkspacePlugin extends Plugin {
    private static final int MAX_RESPONSE_BYTES = 64 * 1024 * 1024;
    private final ExecutorService executor = Executors.newFixedThreadPool(8);
    private final Map<String, HttpURLConnection> requests = new ConcurrentHashMap<>();
    private SharedPreferences preferences;
    private SecretKeySpec masterKey;
    private volatile String profileId;
    private volatile String serverOrigin;
    private volatile String clientVersion;
    private volatile boolean offline;

    @Override
    public void load() {
        preferences = getContext().getSharedPreferences("workspace", Context.MODE_PRIVATE);
        try {
            File file = new File(getContext().getFilesDir(), "master.key");
            byte[] key;
            if (file.isFile()) {
                try (InputStream input = new FileInputStream(file)) { key = readBounded(input, 32); }
                if (key.length != 32) throw new IllegalStateException("Invalid local master key");
            } else {
                key = new byte[32];
                new SecureRandom().nextBytes(key);
                try (FileOutputStream output = new FileOutputStream(file)) { output.write(key); output.getFD().sync(); }
            }
            masterKey = new SecretKeySpec(key, "AES");
        } catch (Exception error) {
            masterKey = null;
        }
    }

    private synchronized String readSecret(String name) throws Exception {
        if (masterKey == null) throw new IllegalStateException("Local credential storage is unavailable");
        String encoded = preferences.getString(name, null);
        if (encoded == null) return null;
        byte[] bytes = Base64.decode(encoded, Base64.NO_WRAP);
        if (bytes.length < 29) throw new IllegalStateException("Invalid encrypted credential");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, masterKey, new GCMParameterSpec(128, bytes, 0, 12));
        cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        return new String(cipher.doFinal(bytes, 12, bytes.length - 12), StandardCharsets.UTF_8);
    }

    private synchronized void writeSecret(String name, String value) throws Exception {
        if (masterKey == null) throw new IllegalStateException("Local credential storage is unavailable");
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, masterKey, new GCMParameterSpec(128, iv));
        cipher.updateAAD(name.getBytes(StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(iv);
        output.write(encrypted);
        if (!preferences.edit().putString(name, Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)).commit()) {
            throw new IllegalStateException("Could not save local credential");
        }
    }

    private JSONObject session(String id) throws Exception {
        String value = readSecret("session:" + id);
        return value == null ? new JSONObject() : new JSONObject(value);
    }

    @PluginMethod
    public void configure(PluginCall call) {
        try {
            String id = call.getString("profileId", "");
            UUID.fromString(id);
            URI origin = validUri(call.getString("origin", ""));
            if ((origin.getPath() != null && !origin.getPath().isEmpty() && !origin.getPath().equals("/")) || origin.getQuery() != null) throw new IllegalArgumentException();
            profileId = id;
            serverOrigin = origin.getScheme() + "://" + origin.getRawAuthority();
            clientVersion = call.getString("clientVersion", "");
            if (!clientVersion.matches("\\d+\\.\\d+\\.\\d+")) throw new IllegalArgumentException();
            offline = call.getBoolean("offline", false);
            JSObject result = new JSObject();
            JSONObject record = session(id);
            result.put("user", record.optJSONObject("user"));
            result.put("expiresAt", record.optString("expiresAt", ""));
            call.resolve(result);
        } catch (Exception error) { call.reject("工作区配置或本地凭据不可用", "WORKSPACE_CONFIGURATION_INVALID"); }
    }

    @PluginMethod
    public void secretGet(PluginCall call) {
        try {
            String key = aiSecretName(call);
            JSObject result = new JSObject();
            result.put("value", readSecret(key));
            call.resolve(result);
        } catch (Exception error) { call.reject("无法读取本机 AI 密钥", "LOCAL_SECRET_UNAVAILABLE"); }
    }

    @PluginMethod
    public void secretSet(PluginCall call) {
        try {
            String value = call.getString("value", "");
            if (value.length() > 50000) throw new IllegalArgumentException();
            writeSecret(aiSecretName(call), value);
            call.resolve();
        } catch (Exception error) { call.reject("无法保存本机 AI 密钥", "LOCAL_SECRET_UNAVAILABLE"); }
    }

    private String aiSecretName(PluginCall call) {
        String id = call.getString("id", "");
        UUID.fromString(id);
        return "ai:" + id;
    }

    private static URI validUri(String value) throws Exception {
        URI uri = new URI(value);
        if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException("Invalid network URL");
        return uri;
    }

    private static byte[] readBounded(InputStream input, int limit) throws Exception {
        if (input == null) return new byte[0];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > limit) throw new IllegalStateException("Response exceeds the byte limit");
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    @PluginMethod
    public void request(PluginCall call) {
        executor.execute(() -> performRequest(call));
    }

    @PluginMethod
    public void cancelRequest(PluginCall call) {
        HttpURLConnection connection = requests.remove(call.getString("requestId", ""));
        if (connection != null) connection.disconnect();
        call.resolve();
    }

    private void performRequest(PluginCall call) {
        String requestId = call.getString("requestId", "");
        HttpURLConnection connection = null;
        boolean resolved = false;
        try {
            if (!requestId.matches("[A-Za-z0-9_-]{1,200}")) throw new IllegalArgumentException();
            URI uri = validUri(call.getString("url", ""));
            String method = call.getString("method", "GET");
            if (!method.matches("GET|HEAD|POST|PUT|PATCH|DELETE|OPTIONS")) throw new IllegalArgumentException();
            String activeProfile = profileId;
            String origin = uri.getScheme() + "://" + uri.getRawAuthority();
            boolean serverRequest = activeProfile != null && origin.equals(serverOrigin);
            if (serverRequest && offline) throw new IllegalStateException("WORKSPACE_OFFLINE");
            byte[] body = Base64.decode(call.getString("body", ""), Base64.NO_WRAP);
            boolean login = serverRequest && method.equals("POST") && (uri.getPath().equals("/api/auth/login") || uri.getPath().equals("/api/auth/register"));
            if (login) {
                JSONObject input = new JSONObject(new String(body, StandardCharsets.UTF_8));
                String deviceId = preferences.getString("device-id", null);
                if (deviceId == null) {
                    deviceId = UUID.randomUUID().toString();
                    if (!preferences.edit().putString("device-id", deviceId).commit()) throw new IllegalStateException();
                }
                input.put("desktopId", deviceId);
                input.put("profileId", activeProfile);
                input.put("clientVersion", clientVersion);
                body = input.toString().getBytes(StandardCharsets.UTF_8);
                uri = new URI(serverOrigin + uri.getPath().replace("/api/auth/", "/api/desktop/auth/"));
            }
            connection = (HttpURLConnection) uri.toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod(method);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(Math.max(1000, Math.min(3600000, call.getInt("timeoutMs", 180000))));
            JSObject headers = call.getObject("headers", new JSObject());
            Iterator<String> names = headers.keys();
            while (names.hasNext()) {
                String name = names.next();
                if (name.equalsIgnoreCase("cookie") || name.equalsIgnoreCase("host") || name.equalsIgnoreCase("origin") || name.equalsIgnoreCase("referer") || name.equalsIgnoreCase("content-length") || (serverRequest && name.equalsIgnoreCase("authorization"))) continue;
                connection.setRequestProperty(name, headers.optString(name));
            }
            if (serverRequest && !login) {
                String token = session(activeProfile).optString("token", "");
                if (!token.isEmpty()) connection.setRequestProperty("Authorization", "Bearer " + token);
            }
            connection.setRequestProperty("Accept-Encoding", "identity");
            requests.put(requestId, connection);
            if (body.length > 0) {
                connection.setDoOutput(true);
                connection.setFixedLengthStreamingMode(body.length);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(body); }
            }
            int status = connection.getResponseCode();
            JSObject responseHeaders = new JSObject();
            for (Map.Entry<String, java.util.List<String>> entry : connection.getHeaderFields().entrySet()) {
                if (entry.getKey() != null && !entry.getKey().equalsIgnoreCase("set-cookie")) responseHeaders.put(entry.getKey().toLowerCase(java.util.Locale.ROOT), String.join(", ", entry.getValue()));
            }
            JSObject result = new JSObject();
            result.put("status", status);
            result.put("headers", responseHeaders);
            InputStream responseStream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            boolean stream = call.getBoolean("stream", false) && !login;
            if (stream) {
                call.resolve(result);
                resolved = true;
                try (InputStream input = responseStream) {
                    byte[] buffer = new byte[16384];
                    int count;
                    int total = 0;
                    while (input != null && (count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_RESPONSE_BYTES) throw new IllegalStateException("Response exceeds the byte limit");
                        JSObject event = new JSObject();
                        event.put("requestId", requestId);
                        event.put("chunk", Base64.encodeToString(buffer, 0, count, Base64.NO_WRAP));
                        notifyListeners("httpStream", event);
                    }
                }
                JSObject done = new JSObject();
                done.put("requestId", requestId);
                done.put("done", true);
                notifyListeners("httpStream", done);
            } else {
                byte[] responseBody;
                try (InputStream input = responseStream) { responseBody = readBounded(input, MAX_RESPONSE_BYTES); }
                if (login && status >= 200 && status < 300) {
                    JSONObject payload = new JSONObject(new String(responseBody, StandardCharsets.UTF_8));
                    JSONObject data = payload.getJSONObject("data");
                    if (data.optString("token").isEmpty() || data.optJSONObject("user") == null) throw new IllegalStateException("Invalid authentication response");
                    writeSecret("session:" + activeProfile, data.toString());
                    data.remove("token");
                    data.put("csrfToken", JSONObject.NULL);
                    responseBody = payload.toString().getBytes(StandardCharsets.UTF_8);
                }
                if (serverRequest && method.equals("GET") && uri.getPath().equals("/api/auth/session") && status == 200) {
                    JSONObject data = new JSONObject(new String(responseBody, StandardCharsets.UTF_8)).optJSONObject("data");
                    if (data != null && data.optBoolean("authenticated") && data.optJSONObject("user") != null) {
                        JSONObject saved = session(activeProfile);
                        saved.put("user", data.getJSONObject("user"));
                        writeSecret("session:" + activeProfile, saved.toString());
                    }
                }
                if (serverRequest && method.equals("DELETE") && uri.getPath().equals("/api/auth/session") && status >= 200 && status < 300) writeSecret("session:" + activeProfile, "{}");
                result.put("body", Base64.encodeToString(responseBody, Base64.NO_WRAP));
                call.resolve(result);
            }
        } catch (Exception error) {
            if (resolved) {
                JSObject event = new JSObject();
                event.put("requestId", requestId);
                event.put("error", "网络请求中断，请重试");
                notifyListeners("httpStream", event);
            } else call.reject("无法连接目标地址，请检查网络与配置", "NETWORK_REQUEST_FAILED");
        } finally {
            requests.remove(requestId);
            if (connection != null) connection.disconnect();
        }
    }

    @PluginMethod
    public void saveFile(PluginCall call) {
        executor.execute(() -> {
            try {
                String name = call.getString("name", "scriverse-export.json").replaceAll("[^\\p{L}\\p{N}._ -]", "-");
                if (name.isEmpty() || name.length() > 180) throw new IllegalArgumentException();
                byte[] bytes = Base64.decode(call.getString("body", ""), Base64.NO_WRAP);
                if (bytes.length > MAX_RESPONSE_BYTES) throw new IllegalArgumentException();
                String mime = call.getString("mimeType", "application/octet-stream").split(";")[0];
                String uri;
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    android.content.ContentValues values = new android.content.ContentValues();
                    values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name);
                    values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mime);
                    values.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/Scriverse");
                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
                    android.net.Uri destination = getContext().getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                    if (destination == null) throw new IllegalStateException();
                    try (java.io.OutputStream output = getContext().getContentResolver().openOutputStream(destination)) {
                        if (output == null) throw new IllegalStateException();
                        output.write(bytes);
                    }
                    values.clear();
                    values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
                    getContext().getContentResolver().update(destination, values, null, null);
                    uri = destination.toString();
                } else {
                    File directory = getContext().getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                    if (directory == null) throw new IllegalStateException();
                    File destination = new File(directory, UUID.randomUUID() + "-" + name);
                    try (FileOutputStream output = new FileOutputStream(destination)) { output.write(bytes); output.getFD().sync(); }
                    uri = destination.getAbsolutePath();
                }
                JSObject result = new JSObject();
                result.put("uri", uri);
                call.resolve(result);
            } catch (Exception error) { call.reject("文件保存失败，请检查设备存储空间", "FILE_SAVE_FAILED"); }
        });
    }

    @PluginMethod
    public void cacheMedia(PluginCall call) {
        executor.execute(() -> {
            try {
                org.json.JSONArray paths = call.getArray("paths");
                if (paths == null || paths.length() > 2000) throw new IllegalArgumentException();
                for (int index = 0; index < paths.length(); index++) {
                    WebResourceResponse response = interceptResource(paths.getString(index));
                    if (response != null && response.getData() != null) response.getData().close();
                }
                call.resolve();
            } catch (Exception error) { call.reject("无法缓存作品图片", "MEDIA_CACHE_FAILED"); }
        });
    }

    WebResourceResponse interceptResource(String path) {
        if (profileId == null || !path.matches("/api/(?:attachments/[^/]+/content|works/[^/]+/cover|users/[^/]+/avatar)(?:\\?.*)?")) return null;
        HttpURLConnection connection = null;
        try {
            String userId = session(profileId).optJSONObject("user").optString("userId", "");
            String identity = profileId + ":" + userId + ":" + path;
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : hash) hex.append(String.format("%02x", value));
            File directory = new File(getContext().getFilesDir(), "media");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IllegalStateException();
            File cached = new File(directory, hex + ".bin");
            String mimeKey = "media-type:" + hex;
            if (!offline) {
                connection = (HttpURLConnection) new URI(serverOrigin + path).toURL().openConnection();
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Authorization", "Bearer " + session(profileId).optString("token"));
                if (connection.getResponseCode() == 200 && connection.getContentType() != null && connection.getContentType().startsWith("image/")) {
                    byte[] bytes;
                    try (InputStream input = connection.getInputStream()) { bytes = readBounded(input, 16000000); }
                    try (FileOutputStream output = new FileOutputStream(cached)) { output.write(bytes); output.getFD().sync(); }
                    preferences.edit().putString(mimeKey, connection.getContentType().split(";")[0]).apply();
                }
            }
            if (cached.isFile()) return new WebResourceResponse(preferences.getString(mimeKey, "image/png"), null, new FileInputStream(cached));
        } catch (Exception error) {
            return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
        } finally { if (connection != null) connection.disconnect(); }
        return new WebResourceResponse("text/plain", "UTF-8", new ByteArrayInputStream(new byte[0]));
    }
}

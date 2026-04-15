package com.czqwq.wikisearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * Lightweight embedded HTTP server that serves the Cloudflare auth helper page and captures the
 * resulting cf_clearance cookie + browser User-Agent, then saves both to {@link Config}.
 *
 * <p>
 * Flow:
 * <ol>
 * <li>User runs {@code /wikisearch auth}.</li>
 * <li>Minecraft opens the system browser at {@code http://localhost:PORT/}.</li>
 * <li>Helper page guides the user to open gtnh.huijiwiki.com and drag a bookmarklet to the toolbar.
 * </li>
 * <li>After the Cloudflare challenge passes the user clicks the bookmarklet on the wiki tab.
 * </li>
 * <li>The bookmarklet calls {@code window.open("http://localhost:PORT/capture?cookie=…&ua=…")} –
 * {@code window.open} navigation is permitted even from HTTPS pages, unlike fetch/XHR.</li>
 * <li>The server writes cookie + UA to Config, notifies Minecraft chat, then shuts down.</li>
 * </ol>
 */
@SideOnly(Side.CLIENT)
public class LocalAuthServer {

    /** Port search range (tries each in order until one binds). */
    private static final int PORT_START = 25581;

    private static final int PORT_END = 25590;

    /** Server auto-shuts-down after this many ms without a successful capture. */
    private static final int ACCEPT_TIMEOUT_MS = 5 * 60 * 1000;

    // ── Singleton ──────────────────────────────────────────────────────────────

    private static volatile LocalAuthServer current;

    /**
     * Stop any running instance and start a fresh server.
     *
     * @return the running server, or {@code null} if no port in the range could be bound.
     */
    public static LocalAuthServer startNew() {
        LocalAuthServer old = current;
        if (old != null) old.shutdown();

        for (int p = PORT_START; p <= PORT_END; p++) {
            try {
                ServerSocket ss = new ServerSocket(p, 1, InetAddress.getLoopbackAddress());
                ss.setSoTimeout(ACCEPT_TIMEOUT_MS);
                LocalAuthServer srv = new LocalAuthServer(ss);
                current = srv;
                Thread t = new Thread(srv::acceptLoop, "WikiSearch-AuthServer-" + p);
                t.setDaemon(true);
                t.start();
                return srv;
            } catch (IOException ignored) {}
        }
        return null;
    }

    // ── Instance ───────────────────────────────────────────────────────────────

    private final ServerSocket serverSocket;
    private final int port;
    private final AtomicBoolean captured = new AtomicBoolean(false);

    private LocalAuthServer(ServerSocket ss) {
        this.serverSocket = ss;
        this.port = ss.getLocalPort();
    }

    public int getPort() {
        return port;
    }

    public boolean isCaptured() {
        return captured.get();
    }

    public void shutdown() {
        try {
            serverSocket.close();
        } catch (IOException ignored) {}
    }

    // ── Accept loop ────────────────────────────────────────────────────────────

    private void acceptLoop() {
        try {
            while (!captured.get()) {
                try {
                    Socket client = serverSocket.accept();
                    client.setSoTimeout(10_000);
                    handleClient(client);
                } catch (SocketTimeoutException e) {
                    GTNHWikiSearch.LOGGER.info("[WikiSearch] Auth server idle timeout – shutting down.");
                    break;
                } catch (IOException e) {
                    if (!serverSocket.isClosed()) {
                        GTNHWikiSearch.LOGGER.warn("[WikiSearch] Auth server accept error", e);
                    }
                    break;
                }
            }
        } finally {
            shutdown();
        }
    }

    // ── Request dispatch ───────────────────────────────────────────────────────

    private void handleClient(Socket client) {
        try (
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter pw = new PrintWriter(
                new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8))) {

            // ── Request line ─────────────────────────────────────────────────
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) return;

            // ── Headers ──────────────────────────────────────────────────────
            int contentLength = 0;
            String headerLine;
            while ((headerLine = reader.readLine()) != null && !headerLine.isEmpty()) {
                if (headerLine.toLowerCase()
                    .startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(
                            headerLine.substring(15)
                                .trim());
                    } catch (NumberFormatException ignored) {}
                }
            }

            // ── Parse method + path ──────────────────────────────────────────
            String[] tokens = requestLine.split(" ", 3);
            if (tokens.length < 2) return;
            String method = tokens[0];
            String fullPath = tokens[1];

            String path = fullPath;
            String query = "";
            int qi = fullPath.indexOf('?');
            if (qi >= 0) {
                path = fullPath.substring(0, qi);
                query = fullPath.substring(qi + 1);
            }

            // ── Route ────────────────────────────────────────────────────────
            switch (path) {
                case "/":
                case "/index.html":
                    writeHtml(pw, buildHelperPage());
                    break;

                case "/status":
                    writeJson(pw, "{\"done\":" + captured.get() + "}");
                    break;

                case "/capture":
                    if ("GET".equals(method)) {
                        handleCapture(query, pw);
                    }
                    break;

                case "/save":
                    if ("OPTIONS".equals(method)) {
                        writeCorsOk(pw);
                    } else if ("POST".equals(method)) {
                        char[] buf = new char[Math.min(Math.max(contentLength, 0), 32768)];
                        int read = reader.read(buf, 0, buf.length);
                        handleSave(pw, new String(buf, 0, Math.max(read, 0)));
                    }
                    break;

                default:
                    pw.print("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
                    pw.flush();
            }
        } catch (Exception e) {
            GTNHWikiSearch.LOGGER.debug("[WikiSearch] Auth server client error", e);
        }
    }

    // ── Handler implementations ────────────────────────────────────────────────

    private void handleCapture(String query, PrintWriter pw) {
        String cookie = getParam(query, "cookie");
        String ua = getParam(query, "ua");

        if (cookie == null || cookie.isEmpty()) {
            writeJson(pw, "{\"ok\":false,\"error\":\"missing cookie\"}");
            return;
        }

        applyAndNotify(cookie, ua);
        writeHtml(pw, successPage());
        shutdown();
    }

    private void handleSave(PrintWriter pw, String body) {
        try {
            JsonObject obj = new JsonParser().parse(body)
                .getAsJsonObject();
            String cookie = obj.has("cookie") ? obj.get("cookie")
                .getAsString() : "";
            String ua = obj.has("ua") ? obj.get("ua")
                .getAsString() : "";

            if (cookie.isEmpty()) {
                writeJson(pw, "{\"ok\":false,\"error\":\"missing cookie\"}");
                return;
            }

            applyAndNotify(cookie, ua);
            writeJson(pw, "{\"ok\":true}");
            shutdown();
        } catch (Exception e) {
            writeJson(pw, "{\"ok\":false,\"error\":\"invalid JSON\"}");
        }
    }

    private void applyAndNotify(String cookie, String ua) {
        Config.setCookie(cookie);
        if (ua != null && !ua.isEmpty()) {
            Config.setUserAgent(ua);
        }
        captured.set(true);

        EntityPlayer player = Minecraft.getMinecraft().thePlayer;
        if (player != null) {
            player.addChatMessage(
                new ChatComponentText(
                    EnumChatFormatting.GOLD + "[WikiSearch] "
                        + EnumChatFormatting.GREEN
                        + "✓ 认证成功！Cookie 已自动保存，现在可以正常搜索了。"));
        }
    }

    // ── HTTP response helpers ──────────────────────────────────────────────────

    private static void writeHtml(PrintWriter pw, String html) {
        int len = html.getBytes(StandardCharsets.UTF_8).length;
        pw.print("HTTP/1.1 200 OK\r\n");
        pw.print("Content-Type: text/html; charset=UTF-8\r\n");
        pw.print("Content-Length: " + len + "\r\n");
        pw.print("Cache-Control: no-store\r\n");
        pw.print("Connection: close\r\n");
        pw.print("\r\n");
        pw.print(html);
        pw.flush();
    }

    private static void writeJson(PrintWriter pw, String json) {
        int len = json.getBytes(StandardCharsets.UTF_8).length;
        pw.print("HTTP/1.1 200 OK\r\n");
        pw.print("Content-Type: application/json\r\n");
        pw.print("Content-Length: " + len + "\r\n");
        pw.print("Access-Control-Allow-Origin: *\r\n");
        pw.print("Cache-Control: no-store\r\n");
        pw.print("Connection: close\r\n");
        pw.print("\r\n");
        pw.print(json);
        pw.flush();
    }

    private static void writeCorsOk(PrintWriter pw) {
        pw.print("HTTP/1.1 204 No Content\r\n");
        pw.print("Access-Control-Allow-Origin: *\r\n");
        pw.print("Access-Control-Allow-Methods: POST, GET, OPTIONS\r\n");
        pw.print("Access-Control-Allow-Headers: Content-Type\r\n");
        pw.print("Connection: close\r\n");
        pw.print("\r\n");
        pw.flush();
    }

    // ── Query-string parser ────────────────────────────────────────────────────

    private static String getParam(String query, String name) {
        for (String kv : query.split("&")) {
            int eq = kv.indexOf('=');
            if (eq < 0) continue;
            if (kv.substring(0, eq)
                .equals(name)) {
                try {
                    return URLDecoder.decode(kv.substring(eq + 1), "UTF-8");
                } catch (Exception e) {
                    return kv.substring(eq + 1);
                }
            }
        }
        return null;
    }

    // ── HTML page generation ───────────────────────────────────────────────────

    /**
     * Builds the auth-helper HTML page.
     *
     * <p>
     * The bookmarklet uses {@code window.open()} (not fetch/XHR) to send the captured cookie to our
     * local server. {@code window.open} navigation from an HTTPS page to {@code http://localhost} is
     * permitted by browsers, whereas fetch/XHR would be blocked as mixed content.
     */
    private String buildHelperPage() {
        // Bookmarklet: runs inside the wiki tab, opens a tiny window to our /capture endpoint.
        // Fallback: if window.open is blocked (popup blocker), alert the user to allow it.
        String bmJs = "javascript:(function(){" + "var u='http://localhost:"
            + port
            + "/capture'"
            + "+'?cookie='+encodeURIComponent(document.cookie)"
            + "+'&ua='+encodeURIComponent(navigator.userAgent);"
            + "var w=window.open(u,'_blank','width=460,height=220,noopener=0');"
            + "if(!w)alert('\\u5f39\\u51fa\\u7a97\\u53e3\\u88ab\\u62e6\\u622a"
            + "\\uff0c\\u8bf7\\u5141\\u8bb8\\u5f39\\u51fa\\u7a97\\u53e3\\u540e\\u91cd\\u8bd5\\u3002');"
            + "})();";

        // HTML-attribute-safe bookmarklet href
        String bmHref = bmJs.replace("&", "&amp;")
            .replace("\"", "&quot;");

        return "<!DOCTYPE html>\n" + "<html lang=\"zh-CN\">\n"
            + "<head>\n"
            + "<meta charset=\"UTF-8\">\n"
            + "<title>WikiSearch 认证助手</title>\n"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
            + "<style>\n"
            + "*{box-sizing:border-box;margin:0;padding:0}\n"
            + "body{font-family:'Microsoft YaHei',Arial,sans-serif;background:#0d1117;"
            + "color:#c9d1d9;display:flex;justify-content:center;"
            + "padding:32px 16px;min-height:100vh}\n"
            + ".card{background:#161b22;border:1px solid #30363d;border-radius:12px;"
            + "padding:28px;max-width:600px;width:100%}\n"
            + "h1{color:#e2b96d;font-size:1.3em;margin-bottom:6px}\n"
            + ".sub{color:#8b949e;font-size:.85em;margin-bottom:24px}\n"
            + ".step{border:1px solid #21262d;border-radius:8px;padding:18px;margin:14px 0}\n"
            + ".step-title{color:#79c0ff;font-weight:bold;margin-bottom:10px}\n"
            + "p{color:#8b949e;font-size:.88em;line-height:1.7;margin-bottom:8px}\n"
            + "b{color:#c9d1d9}\n"
            + ".btn{display:inline-block;padding:8px 18px;border-radius:6px;"
            + "text-decoration:none;font-size:.9em;cursor:pointer;font-family:inherit}\n"
            + ".wiki-btn{background:#1f6feb;color:#fff}\n"
            + ".bm-btn{background:#388bfd;color:#fff;cursor:grab}\n"
            + "#statusBox{margin-top:20px;padding:12px 16px;border-radius:8px;"
            + "background:#161b22;border:1px solid #21262d;"
            + "text-align:center;color:#8b949e;font-size:.9em}\n"
            + "#statusBox.ok{background:#1a3d2b;border-color:#2ea043;color:#56d364}\n"
            + "</style>\n"
            + "</head>\n"
            + "<body>\n"
            + "<div class=\"card\">\n"
            + "  <h1>\uD83D\uDD11 WikiSearch 认证助手</h1>\n"
            + "  <div class=\"sub\">完成 Cloudflare 认证，让 WikiSearch 能正常访问灰机Wiki</div>\n"
            + "\n"
            + "  <div class=\"step\">\n"
            + "    <div class=\"step-title\">第 1 步 &mdash; 打开灰机Wiki</div>\n"
            + "    <p>点击下方按钮，在<b>新标签页</b>打开灰机Wiki。<br>\n"
            + "       如出现 Cloudflare 验证页面，等待其自动通过（通常几秒钟）。<br>\n"
            + "       页面正常显示 Wiki 内容后即可进行第 2 步。</p>\n"
            + "    <a href=\"https://gtnh.huijiwiki.com/wiki/\""
            + " target=\"_blank\" class=\"btn wiki-btn\">\uD83C\uDF10 打开灰机Wiki</a>\n"
            + "  </div>\n"
            + "\n"
            + "  <div class=\"step\">\n"
            + "    <div class=\"step-title\">第 2 步 &mdash; 获取并发送 Cookie</div>\n"
            + "    <p>将下方按钮<b>拖到浏览器书签栏</b>，然后切换到 Wiki 标签页，点击该书签。<br>\n"
            + "       浏览器会自动弹出小窗口将 Cookie 发送给游戏，随后 Minecraft 聊天栏会显示成功提示。</p>\n"
            + "    <a href=\""
            + bmHref
            + "\" class=\"btn bm-btn\">\uD83D\uDCCE 拖我到书签栏</a>\n"
            + "    <p style=\"margin-top:10px;font-size:.82em;\">\uD83D\uDCA1"
            + " 若弹窗被拦截，请在浏览器地址栏右侧<b>允许弹出窗口</b>后重试。</p>\n"
            + "  </div>\n"
            + "\n"
            + "  <div id=\"statusBox\">等待认证...</div>\n"
            + "</div>\n"
            + "<script>\n"
            + "var t=setInterval(function(){\n"
            + "  fetch('/status').then(function(r){return r.json();}).then(function(d){\n"
            + "    if(d.done){\n"
            + "      clearInterval(t);\n"
            + "      var s=document.getElementById('statusBox');\n"
            + "      s.className='ok';\n"
            + "      s.textContent='\u2713 \u8ba4\u8bc1\u6210\u529f\uff01"
            + "Cookie \u5df2\u4fdd\u5b58\uff0c\u8bf7\u8fd4\u56de Minecraft\u3002';\n"
            + "    }\n"
            + "  }).catch(function(){});\n"
            + "},2000);\n"
            + "</script>\n"
            + "</body>\n"
            + "</html>";
    }

    private static String successPage() {
        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
            + "<title>\u8ba4\u8bc1\u6210\u529f</title>"
            + "<style>body{font-family:sans-serif;background:#0d1117;color:#56d364;"
            + "display:flex;align-items:center;justify-content:center;"
            + "height:100vh;margin:0;text-align:center;flex-direction:column;gap:10px}"
            + "p{color:#8b949e;font-size:.9em}</style></head>"
            + "<body>"
            + "<div style=\"font-size:2.5em\">\u2713</div>"
            + "<h2>\u8ba4\u8bc1\u6210\u529f\uff01</h2>"
            + "<p>Cookie \u5df2\u4fdd\u5b58\uff0c\u8bf7\u8fd4\u56de Minecraft\u3002</p>"
            + "<p>\u6b64\u7a97\u53e3\u5c06\u5728 3 \u79d2\u540e\u81ea\u52a8\u5173\u95ed\u3002</p>"
            + "<script>setTimeout(function(){window.close();},3000);</script>"
            + "</body></html>";
    }
}

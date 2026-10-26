package com.dafeiyu.workisland;

import android.content.Intent;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 本地 HTTP 桥：让跑在同一台手机上的 agent（DSHA / Termux / 任何脚本）推工作状态。
 *
 * 为什么不走 adb / am：DSHA 的设备通道只放行白名单命令，`am` 直接被策略拦；
 * 而 proot 里的 Ubuntu 与 Android 共享网络命名空间，所以 127.0.0.1 上的端口
 * 就是最省事的进程间通道 —— 不用 root、不用改 DSHA、不用开权限。
 *
 *   curl "127.0.0.1:8799/state?title=正在读代码&text=Notifier.java&state=reading&todoDone=2&todoTotal=5"
 *   curl "127.0.0.1:8799/done?title=任务完成&text=用时 3 分"
 *   curl "127.0.0.1:8799/idle"
 *   curl "127.0.0.1:8799/cancel"
 *   curl "127.0.0.1:8799/ping"
 *
 * 只绑 127.0.0.1；如果应用里设置过 token，需要带 ?token=xxx。
 */
final class HttpBridge {
    static final int DEFAULT_PORT = 8799;
    private static final String TAG = "WorkIslandHttp";

    private final IslandService service;
    private volatile boolean running;
    private ServerSocket server;
    private Thread thread;
    private int startId = 1;

    HttpBridge(IslandService service) {
        this.service = service;
    }

    void start(int port) {
        if (running) return;
        try {
            server = new ServerSocket(port, 8, InetAddress.getByName("127.0.0.1"));
            running = true;
            thread = new Thread(this::loop, "workisland-http");
            thread.setDaemon(true);
            thread.start();
            Log.i(TAG, "listening on 127.0.0.1:" + port);
        } catch (Throwable t) {
            running = false;
            Log.w(TAG, "bind failed: " + t);
        }
    }

    void stop() {
        running = false;
        try { if (server != null) server.close(); } catch (Throwable ignored) {}
        server = null;
        thread = null;
    }

    private void loop() {
        while (running) {
            Socket socket = null;
            try {
                socket = server.accept();
                handle(socket);
            } catch (Throwable t) {
                if (running) Log.w(TAG, "accept: " + t);
            } finally {
                try { if (socket != null) socket.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private void handle(Socket socket) throws Exception {
        socket.setSoTimeout(3000);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String request = in.readLine();
        if (request == null) return;
        // 吃掉请求头，避免客户端写阻塞
        while (true) {
            String line = in.readLine();
            if (line == null || line.isEmpty()) break;
        }

        String path = "/";
        String query = "";
        String[] parts = request.split(" ");
        if (parts.length >= 2) {
            String target = parts[1];
            int q = target.indexOf('?');
            if (q >= 0) {
                path = target.substring(0, q);
                query = target.substring(q + 1);
            } else {
                path = target;
            }
        }

        Map<String, String> q = parseQuery(query);
        String expected = Prefs.token(service);
        if (expected != null && !expected.isEmpty()
                && !expected.equals(q.get("token"))) {
            respond(socket, 403, "{\"ok\":false,\"error\":\"bad token\"}");
            return;
        }

        String body;
        switch (path) {
            case "/ping":
                body = "{\"ok\":true,\"app\":\"workisland\",\"port\":" + port() + "}";
                break;
            case "/state": body = dispatch(Notifier.ACTION_UPDATE, q); break;
            case "/start": body = dispatch(Notifier.ACTION_START, q); break;
            case "/done": body = dispatch(Notifier.ACTION_DONE, q); break;
            case "/idle": body = dispatch(IslandService.ACTION_IDLE, q); break;
            case "/cancel": body = dispatch(Notifier.ACTION_CANCEL, q); break;
            default: body = "{\"ok\":false,\"error\":\"unknown path\"}"; break;
        }
        respond(socket, 200, body);
    }

    private int port() {
        return server == null ? 0 : server.getLocalPort();
    }

    private String dispatch(String action, Map<String, String> q) {
        try {
            Intent i = new Intent(service, IslandService.class).setAction(action);
            if (q.containsKey("title")) i.putExtra("title", q.get("title"));
            if (q.containsKey("text")) i.putExtra("text", q.get("text"));
            if (q.containsKey("state")) i.putExtra("state", q.get("state"));
            if (q.containsKey("face")) i.putExtra("face", q.get("face"));
            i.putExtra("step", intOf(q, "step", 0));
            i.putExtra("todoDone", intOf(q, "todoDone", 0));
            i.putExtra("todoTotal", intOf(q, "todoTotal", 0));
            long since = q.containsKey("since") ? longOf(q, "since") : System.currentTimeMillis();
            i.putExtra("since", since);
            service.onStartCommand(i, 0, startId++);
            return "{\"ok\":true}";
        } catch (Throwable t) {
            Log.w(TAG, "dispatch: " + t);
            return "{\"ok\":false,\"error\":\"" + escape(String.valueOf(t)) + "\"}";
        }
    }

    private void respond(Socket socket, int code, String body) throws Exception {
        OutputStream out = socket.getOutputStream();
        PrintWriter w = new PrintWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
        w.print("HTTP/1.1 " + code + " OK\r\n");
        w.print("Content-Type: application/json; charset=utf-8\r\n");
        w.print("Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n");
        w.print("Connection: close\r\n\r\n");
        w.print(body);
        w.flush();
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            try {
                map.put(URLDecoder.decode(k, "UTF-8"), URLDecoder.decode(v, "UTF-8"));
            } catch (Throwable ignored) {}
        }
        return map;
    }

    private static int intOf(Map<String, String> q, String key, int def) {
        try {
            String v = q.get(key);
            return v == null ? def : Integer.parseInt(v.trim());
        } catch (Throwable t) {
            return def;
        }
    }

    private static long longOf(Map<String, String> q, String key) {
        try {
            String v = q.get(key);
            return v == null ? System.currentTimeMillis() : Long.parseLong(v.trim());
        } catch (Throwable t) {
            return System.currentTimeMillis();
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}

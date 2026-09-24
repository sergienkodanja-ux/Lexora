package com.lexoravisauls.client.modules.virtualdesktop;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.IntBuffer;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Embedded Headless Chromium Browser Service (Google Chrome / Microsoft Edge).
 * Features:
 * - Anti-Bot / Anti-Captcha: Eliminates infinite captcha loops via navigator.webdriver masking,
 *   real GPU WebGL rendering, persistent profiles (saved cookies & sessions), and modern Chrome fingerprints.
 * - Crystal Clear Audio & Sound: Unmuted sound streaming through Windows audio device, autoplay policy bypassed,
 *   and automatic HTML5 video/audio element unmuting.
 * - Instant Frame Flow & Ultra High FPS: Immediate frame ACKs prevent Chrome screencast queue stalls,
 *   while single-frame off-heap native STB decoding drops stale frames to preserve Minecraft FPS.
 * - Hardware GPU Acceleration: Leverages GPU compositor to free CPU cores for Minecraft tick/render threads.
 */
public final class HeadlessBrowserService {

    private static HeadlessBrowserService instance;

    public static final int BROWSER_WIDTH = 1280;
    public static final int BROWSER_HEIGHT = 720;
    private int cdpPort = 0;

    private Process browserProcess;
    private HttpClient httpClient;
    private volatile WebSocket webSocket;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicInteger msgId = new AtomicInteger(10);

    // Dedicated single-thread decoder to prevent blocking WebSocket network thread
    private ExecutorService frameDecoder;

    // Performance & FPS rate limiting (culling when player > 20 blocks away)
    private volatile boolean cullActive = false;

    // Thread-safe CDP message queues for sequential sending (ackQueue is prioritized for 60 FPS streaming)
    private final ConcurrentLinkedQueue<String> ackQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<String> sendQueue = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean isSending = new AtomicBoolean(false);

    // OpenGL Texture State (accessed on Render thread)
    private int textureId = -1;
    private int texWidth = 0;
    private int texHeight = 0;

    // NativeImage container for zero-copy off-heap frames
    private final AtomicReference<NativeImage> pendingNativeImage = new AtomicReference<>(null);

    // Dynamic frame drop & background decoding
    private final AtomicReference<String> latestBase64Frame = new AtomicReference<>(null);
    private final AtomicBoolean isDecoding = new AtomicBoolean(false);

    // Current page metadata received from Chrome
    private volatile String currentUrl = "https://www.google.com";
    private volatile String pageTitle = "Google";
    private File currentProfileDir;

    private HeadlessBrowserService() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "Lexora-Browser-Shutdown"));
    }

    public static synchronized HeadlessBrowserService getInstance() {
        if (instance == null) {
            instance = new HeadlessBrowserService();
        }
        return instance;
    }

    public boolean isRunning() {
        return running.get();
    }

    public boolean isConnected() {
        return connected.get();
    }

    public String getCurrentUrl() {
        return currentUrl;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public int getTextureId() {
        return textureId;
    }

    public void setCullActive(boolean cull) {
        this.cullActive = cull;
    }

    public synchronized void start() {
        if (running.get()) return;
        running.set(true);

        if (frameDecoder == null || frameDecoder.isShutdown()) {
            frameDecoder = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Lexora-FrameDecoder");
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY - 1);
                return t;
            });
        }

        Thread starter = new Thread(this::initializeBrowser, "Lexora-Browser-Init");
        starter.setDaemon(true);
        starter.start();
    }

    public synchronized void stop() {
        if (!running.get() && browserProcess == null) return;
        running.set(false);
        connected.set(false);

        sendQueue.clear();
        ackQueue.clear();
        isSending.set(false);

        WebSocket ws = this.webSocket;
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "Module disabled");
            } catch (Throwable ignored) {}
            this.webSocket = null;
        }

        if (browserProcess != null) {
            long pid = -1;
            try {
                pid = browserProcess.pid();
            } catch (Throwable ignored) {}

            if (pid > 0) {
                try {
                    new ProcessBuilder("taskkill", "/F", "/T", "/PID", String.valueOf(pid)).start();
                } catch (Throwable ignored) {}
            }

            try {
                browserProcess.descendants().forEach(ProcessHandle::destroyForcibly);
                browserProcess.destroyForcibly();
            } catch (Throwable ignored) {}
            browserProcess = null;
        }

        // Unconditionally terminate any orphan Chrome processes using our lexora browser_data directory
        try {
            new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command",
                    "Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Where-Object { $_.CommandLine -like '*lexora*browser_data*' } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }"
            ).start();
        } catch (Throwable ignored) {}

        if (frameDecoder != null && !frameDecoder.isShutdown()) {
            frameDecoder.shutdownNow();
            frameDecoder = null;
        }

        NativeImage oldImg = pendingNativeImage.getAndSet(null);
        if (oldImg != null) {
            oldImg.close();
        }
        latestBase64Frame.set(null);
    }

    private void initializeBrowser() {
        String execPath = locateBrowserExecutable();
        if (execPath == null) {
            System.err.println("[HeadlessBrowser] Neither Google Chrome nor Microsoft Edge was found on system!");
            running.set(false);
            return;
        }

        try {
            // Setup persistent browser data folder inside Minecraft game directory
            // Preserves cookies, login sessions, and captcha completions permanently
            File gameDir;
            try {
                MinecraftClient mc = MinecraftClient.getInstance();
                gameDir = (mc != null && mc.runDirectory != null) ? mc.runDirectory : new File(".");
            } catch (Throwable t) {
                gameDir = new File(".");
            }
            this.currentProfileDir = new File(gameDir, "lexora/browser_data");
            if (!currentProfileDir.exists()) currentProfileDir.mkdirs();

            // Pre-clean stale lock files from prior abrupt exits
            new File(currentProfileDir, "DevToolsActivePort").delete();
            new File(currentProfileDir, "SingletonLock").delete();
            new File(currentProfileDir, "SingletonCookie").delete();
            new File(currentProfileDir, "SingletonSocket").delete();

            String targetUrl = (currentUrl != null && !currentUrl.isEmpty()) ? currentUrl : "https://www.google.com";

            // Launch Chromium with OS-assigned port (--remote-debugging-port=0), real GPU enabled, and anti-automation stealth flags
            // NOTICE: --mute-audio is intentionally NOT passed so full sound plays through Windows audio device
            ProcessBuilder pb = new ProcessBuilder(
                    execPath,
                    "--headless=new",
                    "--remote-debugging-port=0",
                    "--remote-allow-origins=*",
                    "--no-sandbox",
                    "--disable-blink-features=AutomationControlled",
                    "--enable-gpu",
                    "--enable-gpu-rasterization",
                    "--ignore-gpu-blocklist",
                    "--enable-zero-copy",
                    "--use-angle=d3d11",
                    "--force-device-scale-factor=1",
                    "--high-dpi-support=1",
                    "--window-size=" + BROWSER_WIDTH + "," + BROWSER_HEIGHT,
                    "--autoplay-policy=no-user-gesture-required",
                    "--no-first-run",
                    "--no-default-browser-check",
                    "--disable-background-networking",
                    "--disable-default-apps",
                    "--disable-extensions",
                    "--disable-sync",
                    "--disable-translate",
                    "--hide-scrollbars",
                    "--renderer-process-limit=2",
                    "--disable-features=Translate,OptimizationHints,MediaRouter,DialMediaRouteProvider,CalculateNativeWinOcclusion,InterestFeedContentSuggestions",
                    "--disable-component-update",
                    "--disable-domain-reliability",
                    "--disable-client-side-phishing-detection",
                    "--disable-breakpad",
                    "--disable-crash-reporter",
                    "--disable-dev-shm-usage",
                    "--disable-renderer-backgrounding",
                    "--disable-background-timer-throttling",
                    "--disable-backgrounding-occluded-windows",
                    "--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
                    "--lang=ru-RU,ru,en-US,en",
                    "--user-data-dir=" + currentProfileDir.getAbsolutePath(),
                    targetUrl
            );
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            browserProcess = pb.start();

            // Keep Minecraft threads at top priority on multi-core scheduler
            scheduleLowerProcessPriority(2000L);

            // Detect assigned DevTools port written by Chromium to DevToolsActivePort (up to 20 seconds timeout)
            File portFile = new File(currentProfileDir, "DevToolsActivePort");
            this.cdpPort = -1;
            for (int i = 0; i < 130 && running.get(); i++) {
                if (browserProcess == null || !browserProcess.isAlive()) {
                    System.err.println("[HeadlessBrowser] Browser process exited early with code " + (browserProcess != null ? browserProcess.exitValue() : -1));
                    stop();
                    return;
                }
                if (portFile.exists() && portFile.length() > 0) {
                    try {
                        java.util.List<String> lines = Files.readAllLines(portFile.toPath());
                        if (!lines.isEmpty()) {
                            this.cdpPort = Integer.parseInt(lines.get(0).trim());
                            System.out.println("[HeadlessBrowser] DevTools active port detected: " + this.cdpPort);
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
                Thread.sleep(150);
            }

            if (this.cdpPort <= 0 || !running.get()) {
                System.err.println("[HeadlessBrowser] Failed to detect DevToolsActivePort");
                stop();
                return;
            }

            // Query /json endpoint for target page WebSocket URL
            httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();

            String wsUrl = null;
            for (int attempt = 0; attempt < 30 && running.get(); attempt++) {
                if (browserProcess == null || !browserProcess.isAlive()) {
                    System.err.println("[HeadlessBrowser] Browser process died early!");
                    break;
                }
                try {
                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create("http://127.0.0.1:" + this.cdpPort + "/json"))
                            .timeout(Duration.ofSeconds(2))
                            .GET()
                            .build();
                    HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        wsUrl = extractPageWsUrl(resp.body());
                        if (wsUrl != null) break;
                    }
                } catch (Throwable ignored) {}
                Thread.sleep(200);
            }

            if (wsUrl == null || !running.get()) {
                System.err.println("[HeadlessBrowser] Failed to discover Chrome page endpoint on port " + this.cdpPort);
                stop();
                return;
            }

            // Connect WebSocket
            connectWebSocket(wsUrl, targetUrl);

        } catch (Throwable t) {
            System.err.println("[HeadlessBrowser] Error initializing browser process: " + t.getMessage());
            t.printStackTrace();
            stop();
        }
    }

    private static String extractPageWsUrl(String json) {
        String[] targets = json.split("\\},\\s*\\{");
        for (String t : targets) {
            if (Pattern.compile("\"type\"\\s*:\\s*\"page\"").matcher(t).find()) {
                Matcher m = Pattern.compile("\"webSocketDebuggerUrl\"\\s*:\\s*\"([^\"]+)\"").matcher(t);
                if (m.find()) return m.group(1);
            }
        }
        return null;
    }

    private void connectWebSocket(String wsUrl, String initialUrl) {
        try {
            httpClient.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        private final StringBuilder buffer = new StringBuilder();

                        @Override
                        public void onOpen(WebSocket ws) {
                            webSocket = ws;
                            connected.set(true);
                            System.out.println("[HeadlessBrowser] CDP WebSocket connected successfully!");

                            // Anti-bot stealth + Auto-unmute for all HTML5 media + Fullscreen polyfill & YouTube CSS fix
                            String stealthScript = "Object.defineProperty(navigator, 'webdriver', { get: () => undefined });\n"
                                    + "window.chrome = { app: { isInstalled: false }, runtime: { PlatformOs: { WIN: 'win' } }, loadTimes: function() {}, csi: function() {} };\n"
                                    + "Object.defineProperty(navigator, 'languages', { get: () => ['ru-RU', 'ru', 'en-US', 'en'] });\n"
                                    + "try {\n"
                                    + "  HTMLMediaElement.prototype.play = (function(orig) {\n"
                                    + "    return function() {\n"
                                    + "      this.muted = false;\n"
                                    + "      return orig.apply(this, arguments);\n"
                                    + "    };\n"
                                    + "  })(HTMLMediaElement.prototype.play);\n"
                                    + "} catch(e) {}\n"
                                    + "var _fsEl = null;\n"
                                    + "function _triggerFs() {\n"
                                    + "  document.dispatchEvent(new Event('fullscreenchange', { bubbles: true, cancelable: false }));\n"
                                    + "  document.dispatchEvent(new Event('webkitfullscreenchange', { bubbles: true, cancelable: false }));\n"
                                    + "  window.dispatchEvent(new Event('resize'));\n"
                                    + "}\n"
                                    + "function _enterFs(el) {\n"
                                    + "  _fsEl = el || document.querySelector('#movie_player') || document.querySelector('video') || document.body;\n"
                                    + "  document.documentElement.classList.add('lexora-fullscreen-active');\n"
                                    + "  if (_fsEl && _fsEl.classList) _fsEl.classList.add('lexora-fullscreen', 'ytp-fullscreen');\n"
                                    + "  var p = document.querySelector('#movie_player');\n"
                                    + "  if (p && p.classList) p.classList.add('ytp-fullscreen');\n"
                                    + "  var flexy = document.querySelector('ytd-watch-flexy');\n"
                                    + "  if (flexy) flexy.setAttribute('fullscreen', '');\n"
                                    + "  setTimeout(_triggerFs, 15);\n"
                                    + "}\n"
                                    + "function _exitFs() {\n"
                                    + "  if (_fsEl && _fsEl.classList) _fsEl.classList.remove('lexora-fullscreen', 'ytp-fullscreen');\n"
                                    + "  var p = document.querySelector('#movie_player');\n"
                                    + "  if (p && p.classList) p.classList.remove('ytp-fullscreen');\n"
                                    + "  _fsEl = null;\n"
                                    + "  document.documentElement.classList.remove('lexora-fullscreen-active');\n"
                                    + "  var flexy = document.querySelector('ytd-watch-flexy');\n"
                                    + "  if (flexy) flexy.removeAttribute('fullscreen');\n"
                                    + "  setTimeout(_triggerFs, 15);\n"
                                    + "}\n"
                                    + "Element.prototype.requestFullscreen = function() { _enterFs(this); return Promise.resolve(); };\n"
                                    + "Element.prototype.webkitRequestFullscreen = Element.prototype.requestFullscreen;\n"
                                    + "document.exitFullscreen = function() { _exitFs(); return Promise.resolve(); };\n"
                                    + "document.webkitExitFullscreen = document.exitFullscreen;\n"
                                    + "Object.defineProperty(document, 'fullscreenEnabled', { get: () => true, configurable: true });\n"
                                    + "Object.defineProperty(document, 'webkitFullscreenEnabled', { get: () => true, configurable: true });\n"
                                    + "Object.defineProperty(document, 'fullscreen', { get: () => document.documentElement.classList.contains('lexora-fullscreen-active'), configurable: true });\n"
                                    + "Object.defineProperty(document, 'webkitIsFullScreen', { get: () => document.documentElement.classList.contains('lexora-fullscreen-active'), configurable: true });\n"
                                    + "Object.defineProperty(document, 'fullscreenElement', {\n"
                                    + "  get: function() { return _fsEl || (document.documentElement.classList.contains('lexora-fullscreen-active') ? (document.querySelector('#movie_player') || document.body) : null); },\n"
                                    + "  configurable: true\n"
                                    + "});\n"
                                    + "Object.defineProperty(document, 'webkitFullscreenElement', { get: () => document.fullscreenElement, configurable: true });\n"
                                    + "document.addEventListener('click', function(e) {\n"
                                    + "  var btn = e.target.closest('.ytp-fullscreen-button');\n"
                                    + "  if (btn) {\n"
                                    + "    e.preventDefault(); e.stopPropagation();\n"
                                    + "    if (document.documentElement.classList.contains('lexora-fullscreen-active')) _exitFs(); else _enterFs(document.querySelector('#movie_player'));\n"
                                    + "  }\n"
                                    + "}, true);\n"
                                    + "document.addEventListener('keydown', function(e) {\n"
                                    + "  if (e.key === 'Escape' && document.documentElement.classList.contains('lexora-fullscreen-active')) {\n"
                                    + "    e.preventDefault(); e.stopPropagation(); _exitFs();\n"
                                    + "  } else if ((e.key === 'f' || e.key === 'F' || e.key === 'а' || e.key === 'А') && !e.target.matches('input, textarea, [contenteditable]')) {\n"
                                    + "    if (document.querySelector('#movie_player, video')) {\n"
                                    + "      e.preventDefault(); e.stopPropagation();\n"
                                    + "      if (document.documentElement.classList.contains('lexora-fullscreen-active')) _exitFs(); else _enterFs(document.querySelector('#movie_player'));\n"
                                    + "    }\n"
                                    + "  }\n"
                                    + "}, true);\n"
                                    + "function _addFsCss() {\n"
                                    + "  if (document.getElementById('lexora-fscss')) return;\n"
                                    + "  var s = document.createElement('style');\n"
                                    + "  s.id = 'lexora-fscss';\n"
                                    + "  s.textContent = 'html.lexora-fullscreen-active,html.lexora-fullscreen-active body{overflow:hidden!important;margin:0!important;padding:0!important;width:100vw!important;height:100vh!important;}\\n' +\n"
                                    + "    'html.lexora-fullscreen-active #masthead-container,html.lexora-fullscreen-active #guide,html.lexora-fullscreen-active #secondary,html.lexora-fullscreen-active #below,html.lexora-fullscreen-active #comments,html.lexora-fullscreen-active tp-yt-app-drawer{display:none!important;}\\n' +\n"
                                    + "    'html.lexora-fullscreen-active ytd-app,html.lexora-fullscreen-active #page-manager,html.lexora-fullscreen-active ytd-watch-flexy,html.lexora-fullscreen-active #columns,html.lexora-fullscreen-active #primary,html.lexora-fullscreen-active #primary-inner,html.lexora-fullscreen-active #player,html.lexora-fullscreen-active #player-container-outer,html.lexora-fullscreen-active #player-container-inner,html.lexora-fullscreen-active #player-container,html.lexora-fullscreen-active #player-theater-container,html.lexora-fullscreen-active #full-bleed-container{position:static!important;transform:none!important;filter:none!important;contain:none!important;margin:0!important;padding:0!important;width:100vw!important;height:100vh!important;max-width:100vw!important;max-height:100vh!important;}\\n' +\n"
                                    + "    'html.lexora-fullscreen-active #movie_player,html.lexora-fullscreen-active .html5-video-player,.lexora-fullscreen{position:fixed!important;top:0!important;left:0!important;right:0!important;bottom:0!important;width:100vw!important;height:100vh!important;max-width:100vw!important;max-height:100vh!important;margin:0!important;padding:0!important;transform:none!important;border:none!important;z-index:2147483647!important;background:#000!important;}\\n' +\n"
                                    + "    'html.lexora-fullscreen-active .html5-video-container{position:absolute!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;margin:0!important;padding:0!important;}\\n' +\n"
                                    + "    'html.lexora-fullscreen-active video,.lexora-fullscreen video{position:absolute!important;top:0!important;left:0!important;width:100vw!important;height:100vh!important;max-width:100vw!important;max-height:100vh!important;object-fit:contain!important;transform:none!important;margin:0!important;}\\n' +\n"
                                    + "    '.ytp-chrome-bottom{width:calc(100vw - 24px)!important;left:12px!important;bottom:0!important;}';\n"
                                    + "  (document.head || document.documentElement).appendChild(s);\n"
                                    + "}\n"
                                    + "if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', _addFsCss); else _addFsCss();\n";

                            enqueueSend("{\"id\":1,\"method\":\"Page.enable\",\"params\":{}}");
                            enqueueSend("{\"id\":2,\"method\":\"Page.addScriptToEvaluateOnNewDocument\",\"params\":{\"source\":\"" + escapeJson(stealthScript) + "\"}}");
                            enqueueSend("{\"id\":3,\"method\":\"Emulation.setDeviceMetricsOverride\",\"params\":{\"width\":" + BROWSER_WIDTH + ",\"height\":" + BROWSER_HEIGHT + ",\"deviceScaleFactor\":1,\"mobile\":false}}");
                            // Screencast at quality 40, natural backpressure (steady 35-40 FPS without CPU saturation)
                            enqueueSend("{\"id\":4,\"method\":\"Page.startScreencast\",\"params\":{\"format\":\"jpeg\",\"quality\":40,\"maxWidth\":" + BROWSER_WIDTH + ",\"maxHeight\":" + BROWSER_HEIGHT + ",\"everyNthFrame\":1}}");
                            // Explicit navigation guarantees the page loads immediately even if initial window was blank
                            enqueueSend("{\"id\":5,\"method\":\"Page.navigate\",\"params\":{\"url\":\"" + escapeJson(initialUrl) + "\"}}");

                            lowerProcessPriority();

                            ws.request(1);
                            WebSocket.Listener.super.onOpen(ws);
                        }

                        @Override
                        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
                            buffer.append(data);
                            if (last) {
                                String message = buffer.toString();
                                buffer.setLength(0);
                                handleCdpMessage(message);
                            }
                            ws.request(1);
                            return WebSocket.Listener.super.onText(ws, data, last);
                        }

                        @Override
                        public void onError(WebSocket ws, Throwable error) {
                            System.err.println("[HeadlessBrowser] WebSocket error: " + error.getMessage());
                            connected.set(false);
                            WebSocket.Listener.super.onError(ws, error);
                        }

                        @Override
                        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
                            connected.set(false);
                            return WebSocket.Listener.super.onClose(ws, statusCode, reason);
                        }
                    }).join();

        } catch (Throwable t) {
            System.err.println("[HeadlessBrowser] Failed to connect WebSocket: " + t.getMessage());
            connected.set(false);
        }
    }

    private void sendAck(String json) {
        WebSocket ws = this.webSocket;
        if (ws == null || !connected.get()) return;
        ackQueue.offer(json);
        drainSendQueue();
    }

    private void enqueueSend(String json) {
        WebSocket ws = this.webSocket;
        if (ws == null || !connected.get()) return;
        sendQueue.offer(json);
        drainSendQueue();
    }

    private void drainSendQueue() {
        WebSocket ws = this.webSocket;
        if (ws == null || !connected.get()) return;

        if (isSending.compareAndSet(false, true)) {
            // Prioritize frame ACKs to guarantee continuous 60 FPS pipeline
            String msg = ackQueue.poll();
            if (msg == null) {
                msg = sendQueue.poll();
            }
            if (msg == null) {
                isSending.set(false);
                return;
            }
            try {
                ws.sendText(msg, true).whenComplete((sock, err) -> {
                    isSending.set(false);
                    drainSendQueue();
                });
            } catch (Throwable t) {
                isSending.set(false);
            }
        }
    }

    private void handleCdpMessage(String msg) {
        // 1. Screencast Frame Event
        if (msg.contains("Page.screencastFrame")) {
            long sessionId = 1;
            int sIdx = msg.indexOf("\"sessionId\":");
            if (sIdx != -1) {
                int start = sIdx + 12;
                int end = start;
                while (end < msg.length() && Character.isDigit(msg.charAt(end))) {
                    end++;
                }
                if (end > start) {
                    try {
                        sessionId = Long.parseLong(msg.substring(start, end));
                    } catch (Throwable ignored) {}
                }
            }

            // CRITICAL: High-priority immediate ACK so Chrome never starves
            sendAck("{\"id\":99999,\"method\":\"Page.screencastFrameAck\",\"params\":{\"sessionId\":" + sessionId + "}}");

            // If culled (player > 20 blocks away), do not decode to save 100% CPU/GPU
            if (cullActive) {
                return;
            }

            int dataIdx = msg.indexOf("\"data\":\"");
            if (dataIdx != -1) {
                int endQuote = msg.indexOf('"', dataIdx + 8);
                if (endQuote != -1) {
                    String b64 = msg.substring(dataIdx + 8, endQuote);
                    latestBase64Frame.set(b64);
                    triggerDecode();
                }
            }
            return;
        }

        // 2. Navigation & Title updates (only for main frame, ignoring ads/sub-iframes)
        if (msg.contains("Page.frameNavigated") && !msg.contains("\"parentId\"")) {
            int urlIdx = msg.indexOf("\"url\":\"");
            if (urlIdx != -1) {
                int end = msg.indexOf('"', urlIdx + 7);
                if (end != -1) {
                    String navUrl = msg.substring(urlIdx + 7, end);
                    if (!navUrl.startsWith("about:") && !navUrl.startsWith("chrome:")) {
                        this.currentUrl = navUrl;
                        VirtualDesktopManager.getInstance().onBrowserNavigated(navUrl);
                    }
                }
            }
            // Request document title
            sendCdp("Runtime.evaluate", "{\"expression\":\"document.title\"}");
        }

        // 3. Document Title Response
        if (msg.contains("\"result\":{\"type\":\"string\",\"value\":")) {
            int valIdx = msg.indexOf("\"value\":\"");
            if (valIdx != -1) {
                int end = msg.indexOf('"', valIdx + 9);
                if (end != -1) {
                    String title = msg.substring(valIdx + 9, end);
                    if (!title.isEmpty()) {
                        this.pageTitle = title;
                        VirtualDesktopManager.getInstance().onBrowserTitleChanged(title);
                    }
                }
            }
        }
    }

    public void sendCdp(String method, String paramsJson) {
        if (!connected.get()) return;
        int id = msgId.incrementAndGet();
        String json = "{\"id\":" + id + ",\"method\":\"" + method + "\",\"params\":" + paramsJson + "}";
        enqueueSend(json);
    }

    // --- High-Level Browser Commands ---

    public void navigate(String url) {
        if (url == null || url.trim().isEmpty()) return;
        this.currentUrl = url.trim();
        this.pageTitle = url;
        if (connected.get()) {
            sendCdp("Page.navigate", "{\"url\":\"" + escapeJson(this.currentUrl) + "\"}");
        }
    }

    private int lastSentMouseX = -1;
    private int lastSentMouseY = -1;

    public void sendMouseMove(float normX, float normY) {
        if (!connected.get()) return;
        int cx = (int) (MathHelper.clamp(normX, 0.0f, 1.0f) * BROWSER_WIDTH);
        int cy = (int) (MathHelper.clamp(normY, 0.0f, 1.0f) * BROWSER_HEIGHT);
        if (Math.abs(cx - lastSentMouseX) < 2 && Math.abs(cy - lastSentMouseY) < 2) {
            return;
        }
        lastSentMouseX = cx;
        lastSentMouseY = cy;
        if (sendQueue.size() > 2) {
            sendQueue.removeIf(m -> m.contains("\"mouseMoved\""));
        }
        sendCdp("Input.dispatchMouseEvent", "{\"type\":\"mouseMoved\",\"x\":" + cx + ",\"y\":" + cy + "}");
    }

    public void sendMouseClick(float normX, float normY, int button, boolean isPress) {
        if (!connected.get()) return;
        int cx = (int) (MathHelper.clamp(normX, 0.0f, 1.0f) * BROWSER_WIDTH);
        int cy = (int) (MathHelper.clamp(normY, 0.0f, 1.0f) * BROWSER_HEIGHT);
        String btnStr = (button == 0) ? "left" : ((button == 1) ? "right" : "middle");
        String typeStr = isPress ? "mousePressed" : "mouseReleased";
        sendCdp("Input.dispatchMouseEvent",
                "{\"type\":\"" + typeStr + "\",\"x\":" + cx + ",\"y\":" + cy + ",\"button\":\"" + btnStr + "\",\"clickCount\":1}");
    }

    public void sendMouseWheel(float normX, float normY, double deltaY) {
        if (!connected.get()) return;
        int cx = (int) (MathHelper.clamp(normX, 0.0f, 1.0f) * BROWSER_WIDTH);
        int cy = (int) (MathHelper.clamp(normY, 0.0f, 1.0f) * BROWSER_HEIGHT);
        int dy = (int) (-deltaY * 120);
        sendCdp("Input.dispatchMouseEvent",
                "{\"type\":\"mouseWheel\",\"x\":" + cx + ",\"y\":" + cy + ",\"deltaX\":0,\"deltaY\":" + dy + "}");
    }

    public void sendKey(int key, int scancode, int action, int modifiers) {
        if (!connected.get()) return;
        boolean isDown = (action == GLFW.GLFW_PRESS || action == GLFW.GLFW_REPEAT);
        int vk = mapGlfwToVirtualKey(key);

        if (vk != 0) {
            String type = isDown ? "rawKeyDown" : "keyUp";
            sendCdp("Input.dispatchKeyEvent", "{\"type\":\"" + type + "\",\"windowsVirtualKeyCode\":" + vk + "}");
        }
    }

    public void sendChar(char c) {
        if (!connected.get()) return;
        if (c < 32 && c != '\r' && c != '\n' && c != '\t') return;
        String text = String.valueOf(c).replace("\\", "\\\\").replace("\"", "\\\"");
        sendCdp("Input.dispatchKeyEvent", "{\"type\":\"char\",\"text\":\"" + text + "\"}");
    }

    public void goBack() {
        sendCdp("Runtime.evaluate", "{\"expression\":\"window.history.back()\"}");
    }

    public void goForward() {
        sendCdp("Runtime.evaluate", "{\"expression\":\"window.history.forward()\"}");
    }

    public void reload() {
        sendCdp("Page.reload", "{}");
    }

    private static int mapGlfwToVirtualKey(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_BACKSPACE -> 8;
            case GLFW.GLFW_KEY_TAB -> 9;
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> 13;
            case GLFW.GLFW_KEY_ESCAPE -> 27;
            case GLFW.GLFW_KEY_SPACE -> 32;
            case GLFW.GLFW_KEY_PAGE_UP -> 33;
            case GLFW.GLFW_KEY_PAGE_DOWN -> 34;
            case GLFW.GLFW_KEY_END -> 35;
            case GLFW.GLFW_KEY_HOME -> 36;
            case GLFW.GLFW_KEY_LEFT -> 37;
            case GLFW.GLFW_KEY_UP -> 38;
            case GLFW.GLFW_KEY_RIGHT -> 39;
            case GLFW.GLFW_KEY_DOWN -> 40;
            case GLFW.GLFW_KEY_DELETE -> 46;
            default -> (key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) ? key :
                       ((key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9) ? key : 0);
        };
    }

    private void triggerDecode() {
        if (!running.get() || cullActive) return;
        if (isDecoding.compareAndSet(false, true)) {
            ExecutorService decoder = this.frameDecoder;
            if (decoder != null && !decoder.isShutdown()) {
                decoder.submit(this::processPendingFrames);
            } else {
                isDecoding.set(false);
            }
        }
    }

    private static final long MIN_FRAME_INTERVAL_MS = 16L; // Smooth pacing at ~60 FPS (zero CPU waste, silky smooth video)
    private volatile long lastDecodedTime = 0;

    private void processPendingFrames() {
        try {
            while (running.get() && !cullActive) {
                String b64 = latestBase64Frame.getAndSet(null);
                if (b64 == null) {
                    break;
                }

                long now = System.currentTimeMillis();
                long elapsed = now - lastDecodedTime;
                if (elapsed < MIN_FRAME_INTERVAL_MS) {
                    try {
                        Thread.sleep(MIN_FRAME_INTERVAL_MS - elapsed);
                    } catch (InterruptedException ignored) {}
                    // If a newer frame arrived while sleeping, take it instead
                    String newer = latestBase64Frame.getAndSet(null);
                    if (newer != null) {
                        b64 = newer;
                    }
                }
                lastDecodedTime = System.currentTimeMillis();

                byte[] jpegBytes = Base64.getDecoder().decode(b64);
                NativeImage newImage = null;
                try {
                    newImage = NativeImage.read(new ByteArrayInputStream(jpegBytes));
                } catch (Throwable t) {
                    // Fallback to ImageIO in case STB encounters an uncommon JPEG header
                    try {
                        BufferedImage bi = ImageIO.read(new ByteArrayInputStream(jpegBytes));
                        if (bi != null) {
                            newImage = new NativeImage(NativeImage.Format.RGBA, bi.getWidth(), bi.getHeight(), false);
                            for (int y = 0; y < bi.getHeight(); y++) {
                                for (int x = 0; x < bi.getWidth(); x++) {
                                    int argb = bi.getRGB(x, y);
                                    int a = (argb >> 24) & 0xFF;
                                    int r = (argb >> 16) & 0xFF;
                                    int g = (argb >> 8) & 0xFF;
                                    int b = argb & 0xFF;
                                    newImage.setColorArgb(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                if (newImage != null) {
                    NativeImage old = pendingNativeImage.getAndSet(newImage);
                    if (old != null) {
                        old.close();
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            isDecoding.set(false);
            if (latestBase64Frame.get() != null && running.get() && !cullActive) {
                triggerDecode();
            }
        }
    }

    /**
     * Uploads the latest pending screencast frame into OpenGL texture.
     * Zero-copy direct buffer upload: no byte array iteration or allocation on render thread!
     * Must be called on Minecraft render thread.
     */
    public void updateTexture() {
        NativeImage img = pendingNativeImage.getAndSet(null);
        if (img == null) return;

        try {
            int w = img.getWidth();
            int h = img.getHeight();

            if (textureId == -1) {
                textureId = GlStateManager._genTexture();
                GlStateManager._bindTexture(textureId);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
                GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
                GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
                GlStateManager._pixelStore(GL12.GL_UNPACK_ROW_LENGTH, 0);
                GlStateManager._pixelStore(GL12.GL_UNPACK_SKIP_PIXELS, 0);
                GlStateManager._pixelStore(GL12.GL_UNPACK_SKIP_ROWS, 0);
                GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, BROWSER_WIDTH, BROWSER_HEIGHT, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (IntBuffer) null);
                texWidth = BROWSER_WIDTH;
                texHeight = BROWSER_HEIGHT;
            } else {
                GlStateManager._bindTexture(textureId);
            }

            // Always enforce safe unpack parameters (prevents F3 or font renderer from corrupting browser texture)
            GlStateManager._pixelStore(GL11.GL_UNPACK_ALIGNMENT, 4);
            GlStateManager._pixelStore(GL12.GL_UNPACK_ROW_LENGTH, 0);
            GlStateManager._pixelStore(GL12.GL_UNPACK_SKIP_PIXELS, 0);
            GlStateManager._pixelStore(GL12.GL_UNPACK_SKIP_ROWS, 0);

            if (texWidth < w || texHeight < h) {
                GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                        GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (IntBuffer) null);
                texWidth = w;
                texHeight = h;
            }

            img.upload(0, 0, 0, false);
        } catch (Throwable t) {
            System.err.println("[HeadlessBrowser] Texture upload error: " + t.getMessage());
        } finally {
            img.close();
        }
    }

    private void scheduleLowerProcessPriority(long delayMs) {
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                lowerProcessPriority();
            } catch (InterruptedException ignored) {}
        }, "Lexora-Priority-Adjuster");
        t.setDaemon(true);
        t.start();
    }

    private void lowerProcessPriority() {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return;
        try {
            if (browserProcess != null && browserProcess.isAlive()) {
                long pid = browserProcess.pid();
                if (pid > 0) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(pid);
                    browserProcess.descendants().forEach(ph -> sb.append(",").append(ph.pid()));
                    new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command",
                            "$pids = @(" + sb + "); foreach ($id in $pids) { try { (Get-Process -Id $id -ErrorAction Stop).PriorityClass = [System.Diagnostics.ProcessPriorityClass]::BelowNormal } catch {} }")
                            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                            .redirectError(ProcessBuilder.Redirect.DISCARD)
                            .start();
                }
            }
        } catch (Throwable ignored) {}
    }

    private static String locateBrowserExecutable() {
        String[] candidates = {
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
                "C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe"
        };
        for (String c : candidates) {
            if (c != null && new File(c).exists()) {
                return c;
            }
        }
        return null;
    }

    private static String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\b", "\\b")
                   .replace("\f", "\\f")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }
}

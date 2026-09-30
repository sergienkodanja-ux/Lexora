package com.lexoravisauls.client.utils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Загружает синхронизированные караоке-тексты (LRC) с lrclib.net
 * и предоставляет данные для Dynamic Island в реальном времени.
 *
 * Поддерживает:
 * - Spotify Desktop
 * - YouTube, Yandex Music, VK в браузере (Chrome, Edge, Firefox, Opera, Yandex)
 * - Автоматическую очистку мусорных тегов (Official Video, Clip, 4K, Remastered)
 * - Интеллектуальное ранжирование результатов поиска (Candidate Scoring)
 * - Плавный прогресс караоке без зависаний
 */
public final class LyricsManager {

    public static final class WordTiming {
        public final String word;
        public final long startMs;
        public final long endMs;

        public WordTiming(String word, long startMs, long endMs) {
            this.word = word;
            this.startMs = startMs;
            this.endMs = endMs;
        }
    }

    // ── LRC строка ──────────────────────────────────────────────────────────
    public static final class LyricLine {
        public final long timeMs;   // Время начала строки в мс
        public final String text;   // Текст строки
        public final List<WordTiming> wordTimings; // Точные пословные тайминги (YRC / Enhanced LRC)

        public LyricLine(long timeMs, String text) {
            this(timeMs, text, Collections.emptyList());
        }

        public LyricLine(long timeMs, String text, List<WordTiming> wordTimings) {
            this.timeMs = timeMs;
            this.text = text;
            this.wordTimings = (wordTimings != null) ? wordTimings : Collections.emptyList();
        }

        public boolean hasWordTimings() {
            return wordTimings != null && !wordTimings.isEmpty();
        }
    }

    // ── Состояние ───────────────────────────────────────────────────────────
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Lexora-LyricsManager");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(4))
            .build();

    private static final AtomicBoolean FETCHING = new AtomicBoolean(false);

    /** Ключ текущего загруженного трека ("title|artist") */
    private static volatile String currentKey = "";

    /** Ключ трека, для которого идёт фоновая загрузка */
    private static volatile String requestedKey = "";

    /** Загруженные строки караоке (null = нет текстов / ещё не загружено) */
    private static volatile List<LyricLine> lines = null;

    // Regex для парсинга таймкодов LRC: [mm:ss.xx] или [mm:ss.xxx]
    private static final Pattern TIME_TAG_PATTERN =
            Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{2,3}))?\\]");

    // Regex для очистки названий
    private static final Pattern CLEAN_BRACKETS = Pattern.compile("(?i)\\s*[\\[\\(][^\\]\\)]*[\\]\\)]");
    private static final Pattern CLEAN_FEAT = Pattern.compile("(?i)\\s+(?:feat\\.|ft\\.|prod\\.|featuring|премьера|клип|audio|video|hd|hq|4k|official).*");
    private static final Pattern CLEAN_DASH_TAGS = Pattern.compile("(?i)\\s*-\\s*(?:official|remaster|audio|video|lyrics|clip|hd|4k|visualizer).*");

    // Кэш текстов в памяти: ключ трека -> список строк
    private static final Map<String, List<LyricLine>> CACHE = new ConcurrentHashMap<>();

    private LyricsManager() {}

    // ═════════════════════════════════════════════════════════════════════════
    //  ПУБЛИЧНЫЙ API
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Возвращает уникальный канонический ключ трека, устойчивый к смене сессий плеера.
     */
    public static String getCanonicalKey(String rawTitle, String rawArtist) {
        String[] norm = normalizeMedia(rawTitle, rawArtist);
        return (norm[0] + "|" + norm[1]).toLowerCase(Locale.ROOT);
    }

    /**
     * Вызывается каждый кадр из DynamicIslandRenderer.
     */
    public static void update(String rawTitle, String rawArtist, long durationMs) {
        if (rawTitle == null) rawTitle = "";
        if (rawArtist == null) rawArtist = "";
        if (rawTitle.trim().isEmpty()) return;

        String[] norm = normalizeMedia(rawTitle, rawArtist);
        String title = norm[0];
        String artist = norm[1];
        String normKey = (title + "|" + artist).toLowerCase(Locale.ROOT);

        if (normKey.equals(currentKey)) {
            return; // Этот трек уже активен
        }

        // Проверяем кэш в памяти
        if (CACHE.containsKey(normKey)) {
            List<LyricLine> cached = CACHE.get(normKey);
            lines = (cached != null && !cached.isEmpty()) ? cached : null;
            currentKey = normKey;
            requestedKey = normKey;
            FETCHING.set(false);
            return;
        }

        if (normKey.equals(requestedKey)) {
            return; // Запрос уже выполняется в фоне
        }

        // Сбрасываем старые строки, чтобы они не играли на новом треке во время загрузки
        lines = null;
        currentKey = null;
        requestedKey = normKey;
        FETCHING.set(true);

        final String fTitle = title;
        final String fArtist = artist;
        final long fDur = durationMs;
        final String fKey = normKey;

        EXECUTOR.execute(() -> {
            try {
                List<LyricLine> result = fetchLyricsMultiStage(fTitle, fArtist, fDur);
                if (result != null && !result.isEmpty()) {
                    CACHE.put(fKey, result);
                    if (fKey.equals(requestedKey)) {
                        lines = result;
                        currentKey = fKey;
                    }
                } else {
                    CACHE.put(fKey, Collections.emptyList());
                    if (fKey.equals(requestedKey)) {
                        lines = null;
                        currentKey = fKey;
                    }
                }
            } catch (Throwable t) {
                if (fKey.equals(requestedKey)) {
                    currentKey = fKey;
                }
            } finally {
                FETCHING.set(false);
            }
        });
    }

    /** Есть ли загруженные синхронизированные тексты для текущего трека */
    public static boolean hasLyrics() {
        return lines != null && !lines.isEmpty();
    }

    /** Возвращает копию списка текущих строк караоке (или пустой список) */
    public static List<LyricLine> getLines() {
        List<LyricLine> l = lines;
        return l != null ? new ArrayList<>(l) : Collections.emptyList();
    }

    /** Упреждение звука (80 мс) для идеального попадания в бит и вокал */
    public static final long AUDIO_LEAD_MS = 80L;

    /**
     * Показывает, поётся ли текст прямо сейчас (или идёт инструментальное вступление / долгий проигрыш).
     * Если false — Dynamic Island отображает название трека.
     */
    public static boolean isSinging(long positionMs) {
        List<LyricLine> l = lines;
        if (l == null || l.isEmpty()) return false;
        long effectivePos = positionMs + AUDIO_LEAD_MS;

        long firstLineTime = l.get(0).timeMs;
        // Если до начала первой строки ещё больше 800 мс (вступление / интро) — показываем название трека
        if (effectivePos < firstLineTime - 800L) {
            return false;
        }

        int idx = findLineIndex(l, effectivePos);
        if (idx < 0) {
            // В интервале [firstLineTime - 800ms, firstLineTime] готовим первую строчку
            return true;
        }

        long lineStart = l.get(idx).timeMs;
        long nextStart = (idx + 1 < l.size()) ? l.get(idx + 1).timeMs : lineStart + 4000L;
        long gap = nextStart - lineStart;
        long lineDur = calculateLineDuration(l.get(idx).text, gap);

        // Если текущая строка спета и до следующей строки пауза больше 2.5 секунд (соло / проигрыш)
        if (effectivePos > lineStart + lineDur + 800L && idx + 1 < l.size()) {
            if (nextStart - effectivePos > 1800L) {
                return false; // Показываем название трека во время паузы между куплетами
            }
        }

        return true;
    }

    /** Вычисляет естественную длительность пения строки */
    public static long calculateLineDuration(String text, long gapToNext) {
        if (text == null || text.trim().isEmpty()) {
            return Math.min(gapToNext, 2000L);
        }

        // Если следующая строка близко (до 4 секунд), поём весь промежуток за вычетом микропаузы (150мс)
        if (gapToNext <= 4000L && gapToNext > 400L) {
            return gapToNext - 150L;
        }

        // Если между строками длинная пауза (соло/проигрыш), рассчитываем длительность по слогам
        int wordCount = Math.max(1, text.split("\\s+").length);
        int vowels = Math.max(1, countVowels(text));
        long natural = (long)(vowels * 280L + wordCount * 140L);
        natural = Math.max(2200L, Math.min(5500L, natural));

        return Math.min(gapToNext > 400L ? gapToNext - 200L : gapToNext, natural);
    }

    private static int countVowels(String s) {
        if (s == null) return 0;
        int count = 0;
        String vowels = "aeiouyаеёиоуыэюя";
        for (int i = 0; i < s.length(); i++) {
            if (vowels.indexOf(Character.toLowerCase(s.charAt(i))) >= 0) count++;
        }
        return count;
    }

    /** Текущая строка текста для данной позиции воспроизведения */
    public static String getCurrentLine(long positionMs) {
        if (!isSinging(positionMs)) return "";
        long effectivePos = positionMs + AUDIO_LEAD_MS;
        List<LyricLine> l = lines;
        if (l == null || l.isEmpty()) return "";
        int idx = findLineIndex(l, effectivePos);
        if (idx < 0) {
            return l.get(0).text;
        }
        if (idx >= l.size()) {
            return l.get(l.size() - 1).text;
        }
        return l.get(idx).text;
    }

    /**
     * Прогресс внутри текущей строки (0.0 – 1.0).
     * Плавная интерполяция времени пения строки от её начала до её завершения.
     */
    public static float getLineProgress(long positionMs) {
        long effectivePos = positionMs + AUDIO_LEAD_MS;
        List<LyricLine> l = lines;
        if (l == null || l.isEmpty()) return 0f;
        int idx = findLineIndex(l, effectivePos);
        if (idx < 0) {
            return 0f; // До начала пения первой строки прогресс 0
        }

        LyricLine line = l.get(idx);
        long lineStart = line.timeMs;
        if (effectivePos < lineStart) {
            return 0f;
        }

        // Если в треке есть точные пословные тайминги вокала (YRC / Enhanced LRC)
        if (line.hasWordTimings()) {
            List<WordTiming> wt = line.wordTimings;
            long wStartTotal = wt.get(0).startMs;
            long wEndTotal = wt.get(wt.size() - 1).endMs;
            if (effectivePos <= wStartTotal) return 0f;
            if (effectivePos >= wEndTotal) return 1f;

            for (int i = 0; i < wt.size(); i++) {
                WordTiming w = wt.get(i);
                if (effectivePos >= w.startMs && effectivePos <= w.endMs) {
                    float inWord = (w.endMs > w.startMs) ? (float)(effectivePos - w.startMs) / (float)(w.endMs - w.startMs) : 1f;
                    float wordFraction = 1.0f / wt.size();
                    return (i * wordFraction) + (inWord * wordFraction);
                } else if (i < wt.size() - 1 && effectivePos > w.endMs && effectivePos < wt.get(i + 1).startMs) {
                    // Пауза между словами
                    return (float)(i + 1) / (float)wt.size();
                }
            }
            return 1f;
        }

        long nextStart = (idx + 1 < l.size()) ? l.get(idx + 1).timeMs : lineStart + 4000L;
        long gap = nextStart - lineStart;
        long lineDur = calculateLineDuration(line.text, gap);
        if (lineDur <= 0L) return 1f;

        float progress = (float)(effectivePos - lineStart) / (float)lineDur;
        return Math.max(0f, Math.min(1f, progress));
    }

    /** Первая строка караоке */
    public static String getFirstLine() {
        List<LyricLine> l = lines;
        return (l != null && !l.isEmpty()) ? l.get(0).text : "";
    }

    /** Последняя строка караоке */
    public static String getLastLine() {
        List<LyricLine> l = lines;
        return (l != null && !l.isEmpty()) ? l.get(l.size() - 1).text : "";
    }

    /** Проверяет, идёт ли сейчас загрузка текста */
    public static boolean isFetching() {
        return FETCHING.get();
    }

    // ═════════════════════════════════════════════════════════════════════════
    //  НОРМАЛИЗАЦИЯ И ИНТЕЛЛЕКТУАЛЬНЫЙ ПОИСК
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Извлекает чистые [Title, Artist] из любых источников (YouTube, Spotify, Браузеры).
     */
    public static String[] normalizeMedia(String rawTitle, String rawArtist) {
        String title = rawTitle != null ? rawTitle.trim() : "";
        String artist = rawArtist != null ? rawArtist.trim() : "";

        if (isDummyArtist(artist)) {
            artist = "";
        }

        if (artist.isEmpty()) {
            for (String sep : new String[]{" - ", " – ", " — ", " | ", " // "}) {
                if (title.contains(sep)) {
                    int idx = title.indexOf(sep);
                    String pArtist = title.substring(0, idx).trim();
                    String pTitle = title.substring(idx + sep.length()).trim();
                    if (!pArtist.isEmpty() && !pTitle.isEmpty()) {
                        artist = pArtist;
                        title = pTitle;
                        break;
                    }
                }
            }
        }

        title = cleanTitle(title);
        artist = cleanArtist(artist);

        return new String[]{title, artist};
    }

    private static boolean isDummyArtist(String artist) {
        if (artist == null || artist.isBlank()) return true;
        String a = artist.toLowerCase(Locale.ROOT).trim();
        return a.equals("spotify") || a.equals("youtube") || a.equals("google chrome") || a.equals("chrome")
                || a.equals("firefox") || a.equals("opera") || a.equals("yandex") || a.equals("unknown artist")
                || a.equals("browser") || a.equals("msedge") || a.equals("apple music") || a.equals("vk")
                || a.equals("vkontakte") || a.equals("music") || a.equals("system");
    }

    public static String cleanTitle(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        s = CLEAN_BRACKETS.matcher(s).replaceAll("");
        s = CLEAN_DASH_TAGS.matcher(s).replaceAll("");
        s = CLEAN_FEAT.matcher(s).replaceAll("");
        return s.trim();
    }

    public static String cleanArtist(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (isDummyArtist(s)) return "";
        s = CLEAN_BRACKETS.matcher(s).replaceAll("");
        String[] parts = s.split("(?i)\\s*(?:,|&|feat\\.|ft\\.| x | / )\\s*");
        if (parts.length > 0 && !parts[0].trim().isEmpty()) {
            String p = parts[0].trim();
            if (!isDummyArtist(p)) return p;
        }
        return s;
    }

    /** Поиск синхронизированных текстов (многоуровневый интеллектуальный поиск) */
    private static List<LyricLine> fetchLyricsMultiStage(String title, String artist, long durationMs) {
        String cTitle = cleanTitle(title);
        String cArtist = cleanArtist(artist);

        // 1. Поиск в NetEase пословного YRC караоке (миллисекундная точность каждого слова из базы)
        if (!cTitle.isEmpty()) {
            List<LyricLine> res = fetchFromNetease(cTitle, cArtist, durationMs);
            if (res != null && !res.isEmpty()) return res;
        }

        // 2. Прямой точный запрос к /api/get (LRCLIB)
        if (!cTitle.isEmpty() && !cArtist.isEmpty()) {
            List<LyricLine> res = fetchFromGetEndpoint(cTitle, cArtist, durationMs);
            if (res != null) return res;
        }

        // 3. Полнотекстовый поиск (Исполнитель + Название)
        String query = (cArtist + " " + cTitle).trim();
        List<LyricLine> res = fetchFromSearchEndpoint("q=" + urlEncode(query), cTitle, cArtist, durationMs);
        if (res != null) return res;

        // 4. Полнотекстовый поиск (Название + Исполнитель)
        String queryAlt = (cTitle + " " + cArtist).trim();
        if (!queryAlt.equalsIgnoreCase(query)) {
            res = fetchFromSearchEndpoint("q=" + urlEncode(queryAlt), cTitle, cArtist, durationMs);
            if (res != null) return res;
        }

        // 5. Поиск по структурированным полям track_name и artist_name
        if (!cArtist.isEmpty()) {
            res = fetchFromSearchEndpoint("track_name=" + urlEncode(cTitle) + "&artist_name=" + urlEncode(cArtist), cTitle, cArtist, durationMs);
            if (res != null) return res;
        }

        // 6. Поиск по сырому названию (если в названии были фиты/ремиксы)
        if (!title.equalsIgnoreCase(cTitle) && !title.trim().isEmpty()) {
            res = fetchFromSearchEndpoint("q=" + urlEncode(title), cTitle, cArtist, durationMs);
            if (res != null) return res;
        }

        // 7. Поиск только по названию песни
        if (!cTitle.isEmpty() && !cTitle.equalsIgnoreCase(query)) {
            res = fetchFromSearchEndpoint("q=" + urlEncode(cTitle), cTitle, cArtist, durationMs);
            if (res != null) return res;
        }

        return null;
    }

    /** Прямой запрос к эндпоинту /api/get */
    private static List<LyricLine> fetchFromGetEndpoint(String targetTitle, String targetArtist, long durationMs) {
        try {
            StringBuilder sb = new StringBuilder("https://lrclib.net/api/get?");
            sb.append("track_name=").append(urlEncode(targetTitle));
            sb.append("&artist_name=").append(urlEncode(targetArtist));
            if (durationMs > 5000L) {
                sb.append("&duration=").append(durationMs / 1000L);
            }
            String json = httpGet(sb.toString());
            if (json == null || json.isEmpty()) return null;

            JsonElement root = JsonParser.parseString(json);
            if (root != null && root.isJsonObject()) {
                JsonObject obj = root.getAsJsonObject();
                long introDeltaMs = 0L;
                if (durationMs > 10000L && obj.has("duration") && !obj.get("duration").isJsonNull()) {
                    long lrcDurMs = (long)(obj.get("duration").getAsDouble() * 1000.0);
                    if (lrcDurMs > 10000L) {
                        long diff = durationMs - lrcDurMs;
                        if (Math.abs(diff) >= 3500L && Math.abs(diff) <= 30000L) {
                            introDeltaMs = diff;
                        }
                    }
                }
                if (obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull()) {
                    String lrc = obj.get("syncedLyrics").getAsString();
                    List<LyricLine> parsed = parseLrc(lrc, introDeltaMs);
                    if (!parsed.isEmpty()) return parsed;
                }
                if (obj.has("plainLyrics") && !obj.get("plainLyrics").isJsonNull()) {
                    String plain = obj.get("plainLyrics").getAsString();
                    List<LyricLine> parsed = parsePlainLyrics(plain, durationMs);
                    if (!parsed.isEmpty()) return parsed;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Запрос к эндпоинту /api/search с приоритетом синхронизированного караоке */
    private static List<LyricLine> fetchFromSearchEndpoint(String queryParams, String targetTitle, String targetArtist, long durationMs) {
        try {
            String url = "https://lrclib.net/api/search?" + queryParams;
            String json = httpGet(url);
            if (json == null || json.isEmpty()) return null;

            JsonElement root = JsonParser.parseString(json);
            if (!root.isJsonArray()) return null;

            JsonArray arr = root.getAsJsonArray();
            if (arr.size() == 0) return null;

            String cleanTargetT = cleanTitle(targetTitle).toLowerCase(Locale.ROOT);
            String cleanTargetA = cleanArtist(targetArtist).toLowerCase(Locale.ROOT);

            JsonObject bestObj = null;
            int bestScore = -1;
            boolean bestHasSynced = false;

            for (int i = 0; i < arr.size(); i++) {
                JsonElement elem = arr.get(i);
                if (!elem.isJsonObject()) continue;
                JsonObject obj = elem.getAsJsonObject();

                boolean hasSynced = obj.has("syncedLyrics") && !obj.get("syncedLyrics").isJsonNull() && !obj.get("syncedLyrics").getAsString().trim().isEmpty();
                boolean hasPlain = obj.has("plainLyrics") && !obj.get("plainLyrics").isJsonNull() && !obj.get("plainLyrics").getAsString().trim().isEmpty();

                if (!hasSynced && !hasPlain) continue;

                String trackName = obj.has("trackName") && !obj.get("trackName").isJsonNull() ? obj.get("trackName").getAsString() : "";
                String artistName = obj.has("artistName") && !obj.get("artistName").isJsonNull() ? obj.get("artistName").getAsString() : "";

                String cTrack = cleanTitle(trackName).toLowerCase(Locale.ROOT);
                String cArtist = cleanArtist(artistName).toLowerCase(Locale.ROOT);

                int score = 0;

                // Сопоставление названия трека (0..50 баллов)
                if (!cleanTargetT.isEmpty()) {
                    if (cTrack.equals(cleanTargetT)) {
                        score += 50;
                    } else if (cTrack.contains(cleanTargetT) || cleanTargetT.contains(cTrack)) {
                        score += 35;
                    }
                } else {
                    score += 20;
                }

                // Сопоставление исполнителя (0..40 баллов)
                if (!cleanTargetA.isEmpty()) {
                    if (cArtist.equals(cleanTargetA)) {
                        score += 40;
                    } else if (cArtist.contains(cleanTargetA) || cleanTargetA.contains(cArtist)) {
                        score += 25;
                    }
                } else {
                    score += 15;
                }

                // Точное совпадение по длительности трека (исключает версии с 10-секундным интро из клипов)
                if (durationMs > 0 && obj.has("duration") && !obj.get("duration").isJsonNull()) {
                    double durSec = obj.get("duration").getAsDouble();
                    double userDurSec = durationMs / 1000.0;
                    double diff = Math.abs(durSec - userDurSec);
                    if (diff <= 2.5) {
                        score += 60; // Идеальное совпадение версии альбома
                    } else if (diff <= 5.0) {
                        score += 25;
                    } else if (diff > 7.0) {
                        score -= 50; // Штраф за версию с другим интро
                    }
                }

                // Высокий приоритет синхронизированному LRC караоке (+80 баллов)
                if (hasSynced) {
                    score += 80;
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestObj = obj;
                    bestHasSynced = hasSynced;
                }
            }

            if (bestObj != null && bestScore >= 35) {
                long introDeltaMs = 0L;
                if (durationMs > 10000L && bestObj.has("duration") && !bestObj.get("duration").isJsonNull()) {
                    long lrcDurMs = (long)(bestObj.get("duration").getAsDouble() * 1000.0);
                    if (lrcDurMs > 10000L) {
                        long diff = durationMs - lrcDurMs;
                        // Если трек пользователя длиннее (например, видеоклип с 10-секундным интро), сдвигаем текст
                        if (Math.abs(diff) >= 3500L && Math.abs(diff) <= 30000L) {
                            introDeltaMs = diff;
                        }
                    }
                }

                if (bestHasSynced) {
                    String lrc = bestObj.get("syncedLyrics").getAsString();
                    List<LyricLine> parsed = parseLrc(lrc, introDeltaMs);
                    if (!parsed.isEmpty()) return parsed;
                }
                if (bestObj.has("plainLyrics") && !bestObj.get("plainLyrics").isJsonNull()) {
                    String plain = bestObj.get("plainLyrics").getAsString();
                    List<LyricLine> parsed = parsePlainLyrics(plain, durationMs);
                    if (!parsed.isEmpty()) return parsed;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Выполняет HTTP GET запрос через современный java.net.http.HttpClient */
    private static String httpGet(String urlStr) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(urlStr))
                    .header("User-Agent", "LexoraVisuals/1.0 (https://github.com/lexora)")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                return resp.body();
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return s;
        }
    }

    /** Находит индекс текущей строки по позиции */
    private static int findLineIndex(List<LyricLine> lines, long positionMs) {
        int result = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs <= positionMs) {
                result = i;
            } else {
                break;
            }
        }
        return result;
    }

    private static final Pattern OFFSET_TAG_PATTERN = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE);

    public static List<LyricLine> parseLrc(String lrc) {
        return parseLrc(lrc, 0L);
    }

    /** Парсит LRC-текст в список LyricLine с поддержкой тегов [offset] и компенсацией интро */
    public static List<LyricLine> parseLrc(String lrc, long extraOffsetMs) {
        if (lrc == null || lrc.trim().isEmpty()) return Collections.emptyList();
        List<LyricLine> result = new ArrayList<>();
        String[] rawLines = lrc.split("\\r?\\n");

        long globalOffsetMs = 0L;

        // Первый проход: ищем глобальный тег [offset: +/-ms]
        for (String raw : rawLines) {
            raw = raw.trim();
            Matcher om = OFFSET_TAG_PATTERN.matcher(raw);
            if (om.find()) {
                try {
                    globalOffsetMs = Long.parseLong(om.group(1));
                } catch (Throwable ignored) {}
            }
        }

        for (String raw : rawLines) {
            raw = raw.trim();
            if (raw.isEmpty()) continue;
            if (raw.startsWith("[offset:")) continue;

            Matcher m = TIME_TAG_PATTERN.matcher(raw);
            List<Long> times = new ArrayList<>();
            int lastTagEnd = 0;

            while (m.find()) {
                int minutes = Integer.parseInt(m.group(1));
                int seconds = Integer.parseInt(m.group(2));
                int millis = 0;
                if (m.group(3) != null) {
                    String msStr = m.group(3);
                    if (msStr.length() == 2) {
                        millis = Integer.parseInt(msStr) * 10;
                    } else {
                        millis = Integer.parseInt(msStr);
                    }
                }
                long timeMs = (long) minutes * 60_000L + (long) seconds * 1000L + millis + globalOffsetMs + extraOffsetMs;
                times.add(Math.max(0L, timeMs));
                lastTagEnd = m.end();
            }

            if (!times.isEmpty()) {
                String text = raw.substring(lastTagEnd).trim();
                if (!text.isEmpty()) {
                    for (long t : times) {
                        result.add(new LyricLine(t, text));
                    }
                }
            }
        }

        result.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
        return result;
    }

    /** Парсит обычный текст (plainLyrics), распределяя таймкоды по длительности трека */
    private static List<LyricLine> parsePlainLyrics(String plain, long durationMs) {
        if (plain == null || plain.trim().isEmpty()) return Collections.emptyList();
        String[] rawLines = plain.split("\\r?\\n");
        List<String> validLines = new ArrayList<>();
        for (String s : rawLines) {
            s = s.trim();
            if (!s.isEmpty()) validLines.add(s);
        }
        if (validLines.isEmpty()) return Collections.emptyList();

        long totalDur = durationMs > 10000L ? durationMs : (validLines.size() * 4000L);
        long startOffset = (long) (totalDur * 0.05);
        long usableDur = (long) (totalDur * 0.85);

        List<LyricLine> result = new ArrayList<>();
        for (int i = 0; i < validLines.size(); i++) {
            long timeMs = startOffset + (long) (usableDur * ((double) i / validLines.size()));
            result.add(new LyricLine(timeMs, validLines.get(i)));
        }
        return result;
    }

    private static final Pattern YRC_LINE_PATTERN = Pattern.compile("^\\[(\\d+),\\s*(\\d+)\\](.*)$");
    private static final Pattern YRC_WORD_PATTERN = Pattern.compile("\\((\\d+),\\s*(\\d+),\\s*\\d+\\)([^\\(]*)");

    /** Загружает пословное YRC караоке из базы NetEase Cloud Music */
    private static List<LyricLine> fetchFromNetease(String title, String artist, long durationMs) {
        try {
            String query = (artist + " " + title).trim();
            String searchUrl = "https://music.163.com/api/search/get/web?s=" + urlEncode(query) + "&type=1&offset=0&total=true&limit=3";
            String searchJson = httpGet(searchUrl);
            if (searchJson == null || searchJson.isEmpty()) return null;

            JsonObject root = JsonParser.parseString(searchJson).getAsJsonObject();
            if (!root.has("result") || root.get("result").isJsonNull()) return null;
            JsonObject result = root.getAsJsonObject("result");
            if (!result.has("songs") || !result.get("songs").isJsonArray()) return null;
            JsonArray songs = result.getAsJsonArray("songs");
            if (songs.size() == 0) return null;

            JsonObject firstSong = songs.get(0).getAsJsonObject();
            long songId = firstSong.get("id").getAsLong();

            // Проверяем соответствие длительности трека
            long introDeltaMs = 0L;
            if (durationMs > 10000L && firstSong.has("duration") && !firstSong.get("duration").isJsonNull()) {
                long neteaseDurMs = firstSong.get("duration").getAsLong();
                if (neteaseDurMs > 10000L) {
                    long diff = durationMs - neteaseDurMs;
                    if (Math.abs(diff) >= 3500L && Math.abs(diff) <= 30000L) {
                        introDeltaMs = diff;
                    }
                }
            }

            String lyricUrl = "https://music.163.com/api/song/lyric?id=" + songId + "&lv=-1&kv=-1&tv=-1&yv=-1";
            String lyricJson = httpGet(lyricUrl);
            if (lyricJson == null || lyricJson.isEmpty()) return null;

            JsonObject lyricRoot = JsonParser.parseString(lyricJson).getAsJsonObject();

            // 1. Проверяем пословный YRC-формат (миллисекундная точность нот каждого слова)
            if (lyricRoot.has("yrc") && !lyricRoot.get("yrc").isJsonNull()) {
                JsonObject yrcObj = lyricRoot.getAsJsonObject("yrc");
                if (yrcObj.has("lyric") && !yrcObj.get("lyric").isJsonNull()) {
                    String yrc = yrcObj.get("lyric").getAsString();
                    List<LyricLine> parsed = parseYrc(yrc, introDeltaMs);
                    if (!parsed.isEmpty()) return parsed;
                }
            }

            // 2. Проверяем стандартный синхронизированный LRC
            if (lyricRoot.has("lrc") && !lyricRoot.get("lrc").isJsonNull()) {
                JsonObject lrcObj = lyricRoot.getAsJsonObject("lrc");
                if (lrcObj.has("lyric") && !lrcObj.get("lyric").isJsonNull()) {
                    String lrc = lrcObj.get("lyric").getAsString();
                    List<LyricLine> parsed = parseLrc(lrc, introDeltaMs);
                    if (!parsed.isEmpty()) return parsed;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    /** Парсит пословный формат NetEase YRC */
    public static List<LyricLine> parseYrc(String yrc, long extraOffsetMs) {
        if (yrc == null || yrc.trim().isEmpty()) return Collections.emptyList();
        List<LyricLine> result = new ArrayList<>();
        String[] lines = yrc.split("\\r?\\n");

        for (String lineStr : lines) {
            lineStr = lineStr.trim();
            if (lineStr.isEmpty()) continue;

            Matcher lm = YRC_LINE_PATTERN.matcher(lineStr);
            if (lm.matches()) {
                long lineStart = Long.parseLong(lm.group(1)) + extraOffsetMs;
                String body = lm.group(3);

                Matcher wm = YRC_WORD_PATTERN.matcher(body);
                List<WordTiming> words = new ArrayList<>();
                StringBuilder cleanText = new StringBuilder();

                while (wm.find()) {
                    long wStart = Long.parseLong(wm.group(1)) + extraOffsetMs;
                    long wDur = Long.parseLong(wm.group(2));
                    String wText = wm.group(3);

                    words.add(new WordTiming(wText, Math.max(0L, wStart), Math.max(0L, wStart + wDur)));
                    cleanText.append(wText);
                }

                String fullText = cleanText.toString().trim();
                if (!fullText.isEmpty()) {
                    result.add(new LyricLine(Math.max(0L, lineStart), fullText, words));
                }
            }
        }

        result.sort((a, b) -> Long.compare(a.timeMs, b.timeMs));
        return result;
    }
}


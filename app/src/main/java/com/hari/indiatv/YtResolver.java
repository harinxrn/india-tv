package com.hari.indiatv;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves YouTube pages to playable streams.
 *
 * Layer 1: scrape the watch/live page for an HLS manifest in the embedded
 *          player response.
 * Layer 2: ask YouTube's player API (Innertube) as several client types —
 *          TV, iOS, embedded — for the live HLS manifest.
 * If both fail, callers fall back to the in-app WebView embed player, and
 * finally to the YouTube app.
 */
public class YtResolver {

    private static final String FALLBACK_KEY = "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8";
    private static final Pattern HLS = Pattern.compile("\"hlsManifestUrl\":\"(https:[^\"]+)\"");
    private static final Pattern VID_PATTERNS[] = {
            Pattern.compile("[?&]v=([A-Za-z0-9_-]{11})"),
            Pattern.compile("youtu\\.be/([A-Za-z0-9_-]{11})"),
            Pattern.compile("/(?:shorts|live|embed)/([A-Za-z0-9_-]{11})")
    };
    private static final Pattern CANONICAL = Pattern.compile("rel=\"canonical\" href=\"https://www\\.youtube\\.com/watch\\?v=([A-Za-z0-9_-]{11})");
    private static final Pattern VISITOR = Pattern.compile("\"visitorData\":\"([^\"]+)\"");
    private static final Pattern API_KEY = Pattern.compile("\"INNERTUBE_API_KEY\":\"([^\"]+)\"");
    private static final Pattern STS = Pattern.compile("\"STS\":(\\d+)|signatureTimestamp\":(\\d+)");
    private static final String DESKTOP_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36";

    /** Video id from a URL string alone, or null. */
    public static String videoIdFromUrl(String url) {
        if (url == null) return null;
        for (Pattern p : VID_PATTERNS) {
            Matcher m = p.matcher(url);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    /** Fetch a page and return its text (capped), or null. */
    private static String fetchText(String urlStr, int capBytes) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(20000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", DESKTOP_UA);
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            conn.setRequestProperty("Cookie", "CONSENT=YES+cb.20210328-17-p0.en+FX+999");
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
                if (sb.length() > capBytes) break;
            }
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Resolve a channel /live or handle URL to the current live video id:
     * fetch the page and read its canonical watch URL.
     */
    public static String extractVideoId(String pageUrl) {
        String direct = videoIdFromUrl(pageUrl);
        if (direct != null) return direct;
        String html = fetchText(pageUrl, 1500 * 1024);
        if (html == null) return null;
        Matcher m = CANONICAL.matcher(html);
        if (m.find()) return m.group(1);
        return null;
    }

    /** Full pipeline: page URL -> HLS manifest URL, or null. */
    public static String resolveHls(String pageUrl) {
        try {
            String u = pageUrl.trim();
            if (u.endsWith(".m3u8")) return u;
            if (!u.startsWith("http")) u = "https://" + u;

            String videoId = videoIdFromUrl(u);
            String watchUrl = videoId != null ? "https://www.youtube.com/watch?v=" + videoId : u;

            // Layer 1: scrape the page's embedded player response.
            String html = fetchText(watchUrl, 3 * 1024 * 1024);
            String visitorData = null;
            String apiKey = FALLBACK_KEY;
            String sts = "20000";
            if (html != null) {
                Matcher h = HLS.matcher(html);
                if (h.find()) return unescape(h.group(1));
                Matcher vm = VISITOR.matcher(html);
                if (vm.find()) visitorData = vm.group(1);
                Matcher km = API_KEY.matcher(html);
                if (km.find()) apiKey = km.group(1);
                Matcher sm = STS.matcher(html);
                if (sm.find()) sts = sm.group(1) != null ? sm.group(1) : sm.group(2);
                if (videoId == null) {
                    Matcher cm = CANONICAL.matcher(html);
                    if (cm.find()) videoId = cm.group(1);
                }
            }
            if (videoId == null) return null;

            // Layer 2: Innertube player API, client ladder.
            // {clientNum, clientVersion, clientJson}
            String[][] clients = {
                    {"85", "2.0", "\"clientName\":\"TVHTML5_SIMPLY_EMBEDDED_PLAYER\",\"clientVersion\":\"2.0\""},
                    {"7", "7.20250205.16.00", "\"clientName\":\"TVHTML5\",\"clientVersion\":\"7.20250205.16.00\""},
                    {"28", "1.65.10", "\"clientName\":\"ANDROID_VR\",\"clientVersion\":\"1.65.10\",\"deviceMake\":\"Oculus\",\"deviceModel\":\"Quest 3\",\"androidSdkVersion\":32,\"osName\":\"Android\",\"osVersion\":\"12\""},
                    {"2", "2.20250204.07.00", "\"clientName\":\"MWEB\",\"clientVersion\":\"2.20250204.07.00\""},
                    {"5", "19.09.3", "\"clientName\":\"IOS\",\"clientVersion\":\"19.09.3\",\"deviceMake\":\"Apple\",\"deviceModel\":\"iPhone16,2\",\"osName\":\"iOS\",\"osVersion\":\"17.4.0.22E240\""},
                    {"56", "1.20241009.01.00", "\"clientName\":\"WEB_EMBEDDED_PLAYER\",\"clientVersion\":\"1.20241009.01.00\""}
            };
            for (String[] cl : clients) {
                String hls = innertube(apiKey, visitorData, cl[0], cl[1], cl[2], sts, videoId);
                if (hls != null) return hls;
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private static final Pattern HANDLE = Pattern.compile("@([A-Za-z0-9._\\-]{3,})");
    private static final Pattern CHANNEL_ID = Pattern.compile("youtube\\.com/channel/(UC[A-Za-z0-9_\\-]{10,})");

    /**
     * v2 self-healing rule: store the CHANNEL, never a single video.
     * Any input that names a channel (@handle page, /live page, channel page)
     * is normalized to that channel's permanent /live URL, which always
     * points at whatever the channel is streaming right now. A bare video
     * link (watch?v= / /live/VIDEOID) has no channel in it, so it is kept
     * as-is - it will work until that one stream ends.
     */
    public static String normalizeToLiveUrl(String input) {
        if (input == null) return "";
        String u = input.trim();
        if (u.length() == 0 || u.endsWith(".m3u8")) return u;
        Matcher cm = CHANNEL_ID.matcher(u);
        if (cm.find()) return "https://www.youtube.com/channel/" + cm.group(1) + "/live";
        Matcher hm = HANDLE.matcher(u);
        if (hm.find()) return "https://www.youtube.com/@" + hm.group(1) + "/live";
        return u;
    }

    /** Current live video id for a channel /live URL, fetched fresh. Null if the channel is not live now. */
    public static String liveVideoId(String liveUrl) {
        if (liveUrl == null) return null;
        // Only the canonical lookup is meaningful for /live pages; a direct
        // id in the string would be one specific (possibly ended) stream.
        String html = fetchText(liveUrl.trim(), 1500 * 1024);
        if (html == null) return null;
        Matcher m = CANONICAL.matcher(html);
        if (m.find()) return m.group(1);
        return null;
    }

    /** Light validation: the manifest must answer 200 and look like HLS. */
    public static boolean isPlayableHls(String urlStr) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", DESKTOP_UA);
            if (conn.getResponseCode() != 200) return false;
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            char[] buf = new char[2048];
            int n = r.read(buf);
            r.close();
            if (n <= 0) return false;
            return new String(buf, 0, n).contains("#EXTM3U");
        } catch (Exception e) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String innertube(String apiKey, String visitorData, String clientNum, String clientVersion, String clientJson, String sts, String videoId) {
        HttpURLConnection conn = null;
        try {
            String body = "{\"context\":{\"client\":{" + clientJson + ",\"hl\":\"en\"}"
                    + (clientJson.contains("EMBEDDED")
                        ? ",\"thirdParty\":{\"embedUrl\":\"https://www.youtube.com/embed/" + videoId + "\"}" : "")
                    + "},\"videoId\":\"" + videoId + "\""
                    + ",\"playbackContext\":{\"contentPlaybackContext\":{\"signatureTimestamp\":" + sts + "}}"
                    + ",\"racyCheckOk\":true,\"contentCheckOk\":true}";
            conn = (HttpURLConnection) new URL("https://www.youtube.com/youtubei/v1/player?key=" + apiKey).openConnection();
            conn.setConnectTimeout(12000);
            conn.setReadTimeout(20000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", DESKTOP_UA);
            conn.setRequestProperty("X-YouTube-Client-Name", clientNum);
            conn.setRequestProperty("X-YouTube-Client-Version", clientVersion);
            if (visitorData != null) conn.setRequestProperty("X-Goog-Visitor-Id", visitorData);
            byte[] bytes = body.getBytes("UTF-8");
            OutputStream os = conn.getOutputStream();
            os.write(bytes);
            os.close();
            BufferedReader r = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) {
                sb.append(buf, 0, n);
                if (sb.indexOf("hlsManifestUrl") >= 0 && sb.length() > 4096) break;
                if (sb.length() > 2 * 1024 * 1024) break;
            }
            r.close();
            Matcher m = HLS.matcher(sb.toString());
            if (m.find()) return unescape(m.group(1));
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String unescape(String s) {
        return s.replace("\\u0026", "&").replace("\\/", "/");
    }
}

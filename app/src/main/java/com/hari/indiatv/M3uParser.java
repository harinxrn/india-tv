package com.hari.indiatv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class M3uParser {

    public static List<Channel> parse(InputStream in) throws IOException {
        List<Channel> out = new ArrayList<Channel>();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        Channel pending = null;
        String line;
        while ((line = r.readLine()) != null) {
            line = line.trim();
            if (line.length() == 0) continue;
            if (line.startsWith("#EXTINF")) {
                pending = new Channel();
                pending.logo = attr(line, "tvg-logo");
                String group = attr(line, "group-title");
                if (group != null) {
                    String[] parts = group.split(";");
                    for (String p : parts) {
                        p = p.trim();
                        if (p.length() > 0 && !pending.groups.contains(p)) pending.groups.add(p);
                    }
                }
                int comma = line.lastIndexOf(',');
                pending.name = comma >= 0 ? line.substring(comma + 1).trim() : "Channel";
                pending.recomputeFlags();
            } else if (line.startsWith("#EXTVLCOPT:")) {
                if (pending != null) {
                    String opt = line.substring("#EXTVLCOPT:".length());
                    int eq = opt.indexOf('=');
                    if (eq > 0) {
                        String key = opt.substring(0, eq).trim();
                        String val = opt.substring(eq + 1).trim();
                        if (key.equalsIgnoreCase("http-user-agent")) pending.headers.put("User-Agent", val);
                        else if (key.equalsIgnoreCase("http-referrer")) pending.headers.put("Referer", val);
                        else if (key.equalsIgnoreCase("http-origin")) pending.headers.put("Origin", val);
                    }
                }
            } else if (line.startsWith("#")) {
                // other directives ignored
            } else {
                if (pending == null) pending = new Channel();
                pending.url = line;
                if (pending.name.length() == 0) pending.name = "Channel";
                out.add(pending);
                pending = null;
            }
        }
        return out;
    }

    private static String attr(String line, String key) {
        String pat = key + "=\"";
        int i = line.indexOf(pat);
        if (i < 0) return null;
        int start = i + pat.length();
        int end = line.indexOf('"', start);
        if (end < 0) return null;
        return line.substring(start, end);
    }
}

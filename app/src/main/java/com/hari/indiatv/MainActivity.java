package com.hari.indiatv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String[] SOURCES = {
            "https://iptv-org.github.io/iptv/countries/in.m3u",
            "https://iptv-org.github.io/iptv/languages/mal.m3u",
            "https://iptv-org.github.io/iptv/categories/sports.m3u",
            "https://iptv-org.github.io/iptv/categories/movies.m3u"
    };
    private static final String[] GROUP_ORDER = {
            "News", "Entertainment", "Music", "Kids", "Religious", "General",
            "Documentary", "Series", "Business", "Education", "Lifestyle", "Animation"
    };
    private static final String HOME = "Malayalam News";
    private static final String[] CURATED_NEWS = {
            "Asianet News", "Manorama News", "Mathrubhumi News", "24 News", "Reporter TV",
            "Media One", "News18 Kerala", "Kairali News", "Janam TV", "Raj News Malayalam",
            "Jaihind TV", "Real News Kerala", "Big TV 24x7"
    };
    // Prefilled YouTube Live pages (channel /live URLs). Hari can override
    // any of these per channel with a long-press; stored values win.
    private static final Map<String, String> DEFAULT_YT = new HashMap<String, String>();
    static {
        // Handles verified reachable (HTTP 200 on the /live page) on 2026-10-21.
        DEFAULT_YT.put("asianet news", "https://www.youtube.com/@AsianetNews/live");
        DEFAULT_YT.put("manorama news", "https://www.youtube.com/@ManoramaNews/live");
        DEFAULT_YT.put("news18 kerala", "https://www.youtube.com/@News18Kerala/live");
        DEFAULT_YT.put("24 news", "https://www.youtube.com/@24NewsChannel/live");
        DEFAULT_YT.put("reporter tv", "https://www.youtube.com/@reporterlive/live");
        DEFAULT_YT.put("media one", "https://www.youtube.com/@MediaOneNews/live");
        DEFAULT_YT.put("janam tv", "https://www.youtube.com/@JanamTV/live");
        DEFAULT_YT.put("kairali news", "https://www.youtube.com/@KairaliNews/live");
    }

    private GridView grid;
    private LinearLayout chipRow;
    private HorizontalScrollView chipScroll;
    private TextView status;
    private TextView countLabel;
    private ProgressBar loading;
    private ChannelAdapter adapter;
    private SharedPreferences prefs;

    private List<Channel> rawAll = new ArrayList<Channel>();
    private List<Channel> all = new ArrayList<Channel>();
    private List<Channel> newsTiles = new ArrayList<Channel>();
    private LinkedHashMap<String, List<Channel>> cats = new LinkedHashMap<String, List<Channel>>();
    private String currentCat = "";
    private View currentChip;
    private View lastScaled;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences("ytlinks", MODE_PRIVATE);
        grid = (GridView) findViewById(R.id.grid);
        chipRow = (LinearLayout) findViewById(R.id.chipRow);
        chipScroll = (HorizontalScrollView) chipRow.getParent();
        status = (TextView) findViewById(R.id.status);
        countLabel = (TextView) findViewById(R.id.countLabel);
        loading = (ProgressBar) findViewById(R.id.loading);
        adapter = new ChannelAdapter();
        grid.setAdapter(adapter);

        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                List<Channel> list = adapter.data;
                if (list == null || position >= list.size()) return;
                Channel c = list.get(position);
                if (HOME.equals(currentCat)) {
                    String yt = effectiveYt(c);
                    if (yt != null && yt.length() > 0) {
                        playYouTube(c, yt, list, position);
                        return;
                    }
                }
                playIptv(list, position);
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (!HOME.equals(currentCat)) return false;
                List<Channel> list = adapter.data;
                if (list == null || position >= list.size()) return false;
                showNewsMenu(list.get(position), position);
                return true;
            }
        });
        grid.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (view == null) return;
                if (lastScaled != null && lastScaled != view) {
                    lastScaled.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
                }
                view.animate().scaleX(1.09f).scaleY(1.09f).setDuration(150).start();
                lastScaled = view;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        grid.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN
                        && keyCode == KeyEvent.KEYCODE_DPAD_UP
                        && grid.getSelectedItemPosition() < 5
                        && currentChip != null) {
                    currentChip.requestFocus();
                    return true;
                }
                return false;
            }
        });

        TextView refresh = (TextView) findViewById(R.id.refreshBtn);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadData(true);
            }
        });
        refresh.setOnKeyListener(downToGrid());

        loadData(false);
    }

    private View.OnKeyListener downToGrid() {
        return new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    grid.requestFocus();
                    return true;
                }
                return false;
            }
        };
    }

    private void playIptv(List<Channel> list, int position) {
        AppData.playList = list;
        AppData.playIndex = position;
        AppData.playTitle = currentCat;
        startActivity(new Intent(MainActivity.this, PlayerActivity.class));
    }

    // ---------- HD filter ----------

    private static final Pattern HD_TOKEN = Pattern.compile("(^|[^A-Z0-9])HD([^A-Z0-9]|$)");
    private static final Pattern UHD_TOKEN = Pattern.compile("(^|[^A-Z0-9])(UHD|4K)([^A-Z0-9]|$)");

    static boolean isHd(Channel c) {
        String u = c.name.toUpperCase();
        if (u.contains("720P") || u.contains("1080P") || u.contains("1440P") || u.contains("2160P")) return true;
        if (HD_TOKEN.matcher(u).find()) return true;
        if (UHD_TOKEN.matcher(u).find()) return true;
        return false;
    }

    static String baseName(String name) {
        int p = name.indexOf(" (");
        String b = p > 0 ? name.substring(0, p) : name;
        int b2 = b.indexOf(" [");
        if (b2 > 0) b = b.substring(0, b2);
        return b.trim();
    }

    // ---------- data loading ----------

    private void loadData(final boolean force) {
        if (busy) return;
        busy = true;
        if (!force) {
            List<Channel> cached = readCache();
            if (cached != null && cached.size() > 0) {
                busy = false;
                applyData(cached);
                return;
            }
        }
        loading.setVisibility(View.VISIBLE);
        status.setVisibility(View.VISIBLE);
        status.setText("Loading channels from iptv-org…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Channel> merged = fetchAll();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        loading.setVisibility(View.GONE);
                        if (merged != null && merged.size() > 0) {
                            writeCache(merged);
                            status.setVisibility(View.GONE);
                            applyData(merged);
                        } else if (rawAll.size() > 0) {
                            status.setVisibility(View.GONE);
                            Toast.makeText(MainActivity.this, "Refresh failed — showing saved channels", Toast.LENGTH_LONG).show();
                        } else {
                            status.setText("Could not load channels. Check the TV's internet, then press Refresh.");
                        }
                    }
                });
            }
        }).start();
    }

    private List<Channel> fetchAll() {
        LinkedHashMap<String, Channel> byUrl = new LinkedHashMap<String, Channel>();
        for (int s = 0; s < SOURCES.length; s++) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(SOURCES[s]).openConnection();
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36");
                InputStream in = conn.getInputStream();
                List<Channel> list = M3uParser.parse(in);
                in.close();
                conn.disconnect();
                for (Channel c : list) {
                    if (s == 0) c.india = true;
                    if (s == 1) c.malayalam = true;
                    c.recomputeFlags();
                    Channel existing = byUrl.get(c.url);
                    if (existing == null) byUrl.put(c.url, c);
                    else existing.mergeFrom(c);
                }
            } catch (Exception e) {
                // one bad source should not sink the rest
            }
        }
        if (byUrl.isEmpty()) return null;
        return new ArrayList<Channel>(byUrl.values());
    }

    // ---------- cache (simple TSV, stores the RAW merged list) ----------

    private File cacheFile() {
        return new File(getFilesDir(), "channels.tsv");
    }

    private List<Channel> readCache() {
        try {
            File f = cacheFile();
            if (!f.exists()) return null;
            if (System.currentTimeMillis() - f.lastModified() > 7L * 24 * 3600 * 1000) return null;
            List<Channel> out = new ArrayList<Channel>();
            BufferedReader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                String[] p = line.split("\t", -1);
                if (p.length < 6) continue;
                Channel c = new Channel();
                c.name = URLDecoder.decode(p[0], "UTF-8");
                c.url = p[1];
                c.logo = p[2];
                if (p[3].length() > 0) {
                    for (String g : p[3].split(";")) c.groups.add(g);
                }
                c.india = "1".equals(p[4]);
                c.malayalam = "1".equals(p[5]);
                if (p.length > 6 && p[6].length() > 0) {
                    for (String kv : p[6].split("&")) {
                        int eq = kv.indexOf('=');
                        if (eq > 0) c.headers.put(kv.substring(0, eq), URLDecoder.decode(kv.substring(eq + 1), "UTF-8"));
                    }
                }
                c.recomputeFlags();
                out.add(c);
            }
            r.close();
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private void writeCache(final List<Channel> list) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    OutputStreamWriter w = new OutputStreamWriter(new FileOutputStream(cacheFile()), "UTF-8");
                    for (Channel c : list) {
                        StringBuilder sb = new StringBuilder();
                        sb.append(URLEncoder.encode(c.name, "UTF-8")).append('\t');
                        sb.append(c.url).append('\t');
                        sb.append(c.logo).append('\t');
                        for (int i = 0; i < c.groups.size(); i++) {
                            if (i > 0) sb.append(';');
                            sb.append(c.groups.get(i));
                        }
                        sb.append('\t').append(c.india ? '1' : '0').append('\t').append(c.malayalam ? '1' : '0').append('\t');
                        boolean first = true;
                        for (Map.Entry<String, String> e : c.headers.entrySet()) {
                            if (!first) sb.append('&');
                            first = false;
                            sb.append(e.getKey()).append('=').append(URLEncoder.encode(e.getValue(), "UTF-8"));
                        }
                        sb.append('\n');
                        w.write(sb.toString());
                    }
                    w.close();
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    // ---------- categories + UI ----------

    private void applyData(List<Channel> raw) {
        rawAll = raw;
        all = new ArrayList<Channel>();
        for (Channel c : rawAll) {
            if (isHd(c)) all.add(c);
        }
        buildNewsTiles();

        cats.clear();
        List<Channel> mal = new ArrayList<Channel>();
        List<Channel> sports = new ArrayList<Channel>();
        List<Channel> movies = new ArrayList<Channel>();
        List<Channel> india = new ArrayList<Channel>();
        for (Channel c : all) {
            if (c.malayalam) mal.add(c);
            if (c.sports) sports.add(c);
            if (c.movies) movies.add(c);
            if (c.india) india.add(c);
        }
        if (!mal.isEmpty()) cats.put("Malayalam", mal);
        if (!sports.isEmpty()) cats.put("Sports", sports);
        if (!movies.isEmpty()) cats.put("Movies", movies);
        if (!india.isEmpty()) cats.put("India", india);
        for (String g : GROUP_ORDER) {
            List<Channel> gl = new ArrayList<Channel>();
            for (Channel c : all) {
                for (String cg : c.groups) {
                    if (cg.equalsIgnoreCase(g)) { gl.add(c); break; }
                }
            }
            if (gl.size() >= 3 && !cats.containsKey(g)) cats.put(g, gl);
        }
        cats.put("All Channels", all);

        Comparator<Channel> byName = new Comparator<Channel>() {
            @Override
            public int compare(Channel a, Channel b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        };
        for (List<Channel> l : cats.values()) Collections.sort(l, byName);

        buildChips();
        if (!newsTiles.isEmpty()) selectCategory(HOME);
        else selectCategory(cats.keySet().iterator().next());
        if (currentChip != null) currentChip.requestFocus();
    }

    private void buildNewsTiles() {
        LinkedHashMap<String, Channel> best = new LinkedHashMap<String, Channel>();
        for (Channel c : rawAll) {
            if (!c.malayalam) continue;
            boolean isNews = false;
            for (String g : c.groups) if (g.equalsIgnoreCase("News")) { isNews = true; break; }
            String base = baseName(c.name);
            boolean curated = false;
            for (String n : CURATED_NEWS) if (n.equalsIgnoreCase(base)) { curated = true; break; }
            if (!isNews && !curated) continue;
            Channel prev = best.get(base.toLowerCase());
            if (prev == null || (isHd(c) && !isHd(prev))) best.put(base.toLowerCase(), c);
        }
        newsTiles = new ArrayList<Channel>(best.values());
        Collections.sort(newsTiles, new Comparator<Channel>() {
            @Override
            public int compare(Channel a, Channel b) {
                int ia = curatedIndex(baseName(a.name));
                int ib = curatedIndex(baseName(b.name));
                if (ia != ib) return ia - ib;
                return a.name.compareToIgnoreCase(b.name);
            }
        });
    }

    private static int curatedIndex(String base) {
        for (int i = 0; i < CURATED_NEWS.length; i++) {
            if (CURATED_NEWS[i].equalsIgnoreCase(base)) return i;
        }
        return 1000;
    }

    private void buildChips() {
        chipRow.removeAllViews();
        TextView search = makeChip("⌕ Search", false);
        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showSearch();
            }
        });
        chipRow.addView(search);
        if (!newsTiles.isEmpty()) {
            chipRow.addView(makeChip(HOME, true));
        }
        for (final String name : cats.keySet()) {
            chipRow.addView(makeChip(name, true));
        }
    }

    private TextView makeChip(final String text, final boolean isCategory) {
        final TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xFFF5F5F7);
        t.setTextSize(15);
        t.setBackgroundResource(R.drawable.chip_bg);
        t.setFocusable(true);
        t.setClickable(true);
        int h = (int) (16 * getResources().getDisplayMetrics().density);
        int v = (int) (8 * getResources().getDisplayMetrics().density);
        t.setPadding(h, v, h, v);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, (int) (10 * getResources().getDisplayMetrics().density), 0);
        t.setLayoutParams(lp);
        t.setOnKeyListener(downToGrid());
        if (isCategory) {
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    selectCategory(text);
                    grid.requestFocus();
                }
            });
            t.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View view, boolean hasFocus) {
                    if (hasFocus) {
                        selectCategory(text);
                        Rect r = new Rect();
                        t.getDrawingRect(r);
                        chipRow.offsetDescendantRectToMyCoords(t, r);
                        chipScroll.requestChildRectangleOnScreen(chipRow, r, false);
                    }
                }
            });
        }
        return t;
    }

    private void markSelectedChip() {
        currentChip = null;
        for (int i = 0; i < chipRow.getChildCount(); i++) {
            TextView t = (TextView) chipRow.getChildAt(i);
            boolean sel = t.getText().toString().equals(currentCat);
            t.setSelected(sel);
            if (sel) currentChip = t;
        }
    }

    private void selectCategory(String name) {
        List<Channel> list;
        if (HOME.equals(name)) list = newsTiles;
        else list = cats.get(name);
        if (list == null) return;
        boolean same = name.equals(currentCat);
        currentCat = name;
        if (!same) {
            if (lastScaled != null) {
                lastScaled.setScaleX(1f);
                lastScaled.setScaleY(1f);
                lastScaled = null;
            }
            adapter.setData(list);
            if (list.size() > 0) grid.setSelection(0);
        }
        if (HOME.equals(name)) {
            countLabel.setText(list.size() + " news channels • YouTube + IPTV");
            status.setText("OK = play (YouTube if a link is set, else IPTV) • Hold OK = options / add YouTube link • ▶YT = YouTube set");
            status.setVisibility(View.VISIBLE);
        } else {
            countLabel.setText(list.size() + " channels • HD only");
            status.setVisibility(View.GONE);
        }
        markSelectedChip();
    }

    private void showSearch() {
        final EditText input = new EditText(this);
        input.setHint("Channel name");
        new AlertDialog.Builder(this)
                .setTitle("Search HD channels")
                .setView(input)
                .setPositiveButton("Search", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String q = input.getText().toString().trim().toLowerCase();
                        if (q.length() == 0) return;
                        List<Channel> res = new ArrayList<Channel>();
                        for (Channel c : all) {
                            if (c.name.toLowerCase().contains(q)) res.add(c);
                        }
                        if (res.isEmpty()) {
                            Toast.makeText(MainActivity.this, "No HD channels match '" + q + "'", Toast.LENGTH_LONG).show();
                            return;
                        }
                        currentCat = "Search";
                        adapter.setData(res);
                        countLabel.setText(res.size() + " results for '" + q + "'");
                        status.setVisibility(View.GONE);
                        markSelectedChip();
                        grid.requestFocus();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ---------- Malayalam News: YouTube links ----------

    private String ytKey(Channel c) {
        return "yt_" + baseName(c.name).toLowerCase();
    }

    /** Effective YouTube link: user's saved value wins; "" means user cleared it; else the prefilled default. */
    private String effectiveYt(Channel c) {
        if (prefs.contains(ytKey(c))) return prefs.getString(ytKey(c), "");
        String d = DEFAULT_YT.get(baseName(c.name).toLowerCase());
        return d == null ? "" : d;
    }

    private void showNewsMenu(final Channel c, final int position) {
        final String yt = effectiveYt(c);
        final List<String> labels = new ArrayList<String>();
        final List<Integer> actions = new ArrayList<Integer>();
        labels.add("▶ Play YouTube Live"); actions.add(0);
        labels.add("▶ Play IPTV stream"); actions.add(1);
        labels.add("✎ " + (yt.length() > 0 ? "Edit" : "Add") + " YouTube link"); actions.add(2);
        if (yt.length() > 0) { labels.add("✕ Remove YouTube link"); actions.add(3); }
        String[] items = labels.toArray(new String[labels.size()]);
        new AlertDialog.Builder(this)
                .setTitle(baseName(c.name))
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int act = actions.get(which);
                        if (act == 0) {
                            if (yt.length() == 0) {
                                Toast.makeText(MainActivity.this, "No YouTube link yet — add one first", Toast.LENGTH_LONG).show();
                                editYtLink(c);
                            } else {
                                playYouTube(c, yt, adapter.data, position);
                            }
                        } else if (act == 1) {
                            playIptv(adapter.data, position);
                        } else if (act == 2) {
                            editYtLink(c);
                        } else if (act == 3) {
                            prefs.edit().putString(ytKey(c), "").apply();
                            adapter.notifyDataSetChanged();
                            Toast.makeText(MainActivity.this, "YouTube link removed — this tile now plays IPTV", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }

    private void editYtLink(final Channel c) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(effectiveYt(c));
        input.setSelection(input.getText().length());
        input.setHint("https://www.youtube.com/@channel/live");
        new AlertDialog.Builder(this)
                .setTitle("YouTube Live link — " + baseName(c.name))
                .setMessage("Paste the channel's live page, a watch link, or a direct .m3u8. Saved on this TV only.")
                .setView(input)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String val = input.getText().toString().trim();
                        prefs.edit().putString(ytKey(c), val).apply();
                        adapter.notifyDataSetChanged();
                        Toast.makeText(MainActivity.this,
                                val.length() > 0 ? "Saved. OK on this tile now plays YouTube." : "Cleared. This tile now plays IPTV.",
                                Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void playYouTube(final Channel c, final String link, final List<Channel> iptvList, final int iptvPos) {
        Toast.makeText(this, "Opening YouTube live for " + baseName(c.name) + "…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Best: pull the live stream and play it in the app's own player.
                final String m3u8 = YtResolver.resolveHls(link);
                if (m3u8 != null) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Channel sc = new Channel();
                            sc.name = baseName(c.name) + " - YouTube Live";
                            sc.url = m3u8;
                            sc.logo = c.logo;
                            List<Channel> single = new ArrayList<Channel>();
                            single.add(sc);
                            AppData.playList = single;
                            AppData.playIndex = 0;
                            AppData.playTitle = "YouTube Live";
                            startActivity(new Intent(MainActivity.this, PlayerActivity.class));
                        }
                    });
                    return;
                }
                // Next best: the official embedded player, still inside the app.
                final String videoId = YtResolver.extractVideoId(link);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (videoId != null) {
                            // IPTV context for the web player's escape hatch.
                            AppData.playList = iptvList;
                            AppData.playIndex = iptvPos;
                            AppData.playTitle = currentCat;
                            Intent i = new Intent(MainActivity.this, WebPlayerActivity.class);
                            i.putExtra(WebPlayerActivity.EXTRA_VIDEO_ID, videoId);
                            i.putExtra(WebPlayerActivity.EXTRA_TITLE, baseName(c.name) + " - YouTube Live");
                            i.putExtra(WebPlayerActivity.EXTRA_ORIGINAL_URL, link);
                            startActivity(i);
                        } else {
                            openInYouTubeApp(link);
                        }
                    }
                });
            }
        }).start();
    }

    private void openInYouTubeApp(String link) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
            i.setPackage("com.google.android.youtube.tv");
            startActivity(i);
        } catch (ActivityNotFoundException e1) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(this, "Couldn't open that YouTube link — check it, or use the IPTV stream", Toast.LENGTH_LONG).show();
            }
        }
    }

    // ---------- grid adapter ----------

    private class ChannelAdapter extends BaseAdapter {
        List<Channel> data = new ArrayList<Channel>();

        void setData(List<Channel> d) {
            data = d;
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public Object getItem(int position) {
            return data.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_channel, parent, false);
            }
            Channel c = data.get(position);
            ImageView logo = (ImageView) v.findViewById(R.id.logo);
            TextView name = (TextView) v.findViewById(R.id.name);
            if (HOME.equals(currentCat)) {
                String label = baseName(c.name);
                if (effectiveYt(c).length() > 0) label += "  ▶YT";
                name.setText(label);
            } else {
                name.setText(c.name);
            }
            ImageLoader.load(logo, c.logo);
            return v;
        }
    }
}

package com.hari.indiatv;

import android.app.Activity;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v2 player: clean Apple-TV-style bottom panel (logo, LIVE badge, channel
 * name, position, hints) that fades out, plus YouTube self-healing - if a
 * YouTube HLS stream dies mid-play, the channel's latest live is resolved
 * fresh and playback resumes instead of skipping / going dead.
 */
public class PlayerActivity extends Activity {

    private List<Channel> list;
    private int index;
    private MediaPlayer mp;
    private SurfaceView surface;
    private SurfaceHolder holder;
    private ProgressBar buffering;
    private TextView bufferingLabel;
    private View panel;
    private TextView nowPlaying;
    private TextView subInfo;
    private ImageView channelLogo;
    private Handler handler = new Handler();
    private int errorStreak = 0;
    private int ytRetry = 0;
    private boolean stopped = false;
    private boolean paused = false;
    private boolean resolving = false;

    private final Runnable hideOverlay = new Runnable() {
        @Override
        public void run() {
            if (!paused) panel.setVisibility(View.GONE);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_player);
        list = AppData.playList;
        index = AppData.playIndex;
        if (list == null || list.isEmpty()) {
            finish();
            return;
        }
        surface = (SurfaceView) findViewById(R.id.surface);
        buffering = (ProgressBar) findViewById(R.id.buffering);
        bufferingLabel = (TextView) findViewById(R.id.bufferingLabel);
        panel = findViewById(R.id.playerPanel);
        nowPlaying = (TextView) findViewById(R.id.nowPlaying);
        subInfo = (TextView) findViewById(R.id.subInfo);
        channelLogo = (ImageView) findViewById(R.id.channelLogo);
        holder = surface.getHolder();
        holder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder h) {
                play(index);
            }

            @Override
            public void surfaceChanged(SurfaceHolder h, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder h) {
                releasePlayer();
            }
        });
    }

    private Map<String, String> headersFor(Channel c) {
        Map<String, String> h = new HashMap<String, String>(c.headers);
        if (!h.containsKey("User-Agent")) {
            h.put("User-Agent", "Mozilla/5.0 (Linux; Android 12; Smart TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
        }
        return h;
    }

    private void showOverlay(long hideAfterMs) {
        final Channel c = list.get(index);
        nowPlaying.setText(displayName(c.name));
        String section = AppData.playTitle != null && AppData.playTitle.length() > 0 ? AppData.playTitle : "Live TV";
        String src = c.ytLiveUrl != null ? "YouTube Live" : "Live TV";
        subInfo.setText(section + "  -  " + src + "  -  " + (index + 1) + " of " + list.size());
        ImageLoader.load(channelLogo, c.logo);
        panel.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOverlay);
        if (hideAfterMs > 0) handler.postDelayed(hideOverlay, hideAfterMs);
    }

    private static String displayName(String name) {
        int p = name.indexOf(" (");
        String b = p > 0 ? name.substring(0, p) : name;
        int b2 = b.indexOf(" [");
        if (b2 > 0) b = b.substring(0, b2);
        return b.trim();
    }

    private void play(int i) {
        if (list == null || list.isEmpty()) return;
        index = ((i % list.size()) + list.size()) % list.size();
        final Channel c = list.get(index);
        stopped = false;
        paused = false;
        releasePlayer();
        showOverlay(4500);
        buffering.setVisibility(View.VISIBLE);
        bufferingLabel.setVisibility(View.VISIBLE);
        bufferingLabel.setText("Loading " + displayName(c.name) + "...");
        try {
            mp = new MediaPlayer();
            mp.setDisplay(holder);
            mp.setDataSource(getApplicationContext(), Uri.parse(c.url), headersFor(c));
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer player) {
                    errorStreak = 0;
                    ytRetry = 0;
                    buffering.setVisibility(View.GONE);
                    bufferingLabel.setVisibility(View.GONE);
                    player.start();
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer player, int what, int extra) {
                    buffering.setVisibility(View.GONE);
                    bufferingLabel.setVisibility(View.GONE);
                    // YouTube live HLS urls expire / rotate when a stream
                    // restarts: re-resolve the channel's latest live first.
                    if (c.ytLiveUrl != null && ytRetry < 2 && !resolving) {
                        ytRetry++;
                        resolveYouTubeAgain(c);
                        return true;
                    }
                    errorStreak++;
                    if (list.size() <= 1 || errorStreak >= 12) {
                        nowPlaying.setText(displayName(c.name));
                        subInfo.setText("Stream unavailable right now. Back to go home, or Up / Down for another channel.");
                        panel.setVisibility(View.VISIBLE);
                        handler.removeCallbacks(hideOverlay);
                        return true;
                    }
                    Toast.makeText(PlayerActivity.this, "Could not play " + displayName(c.name) + " - skipping", Toast.LENGTH_SHORT).show();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!stopped) play(index + 1);
                        }
                    }, 700);
                    return true;
                }
            });
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer player) {
                    // A live stream that "completes" has ended - for YouTube,
                    // that usually means a new live has already started.
                    if (c.ytLiveUrl != null && ytRetry < 2 && !resolving) {
                        ytRetry++;
                        resolveYouTubeAgain(c);
                    }
                }
            });
            mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer player, int what, int extra) {
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
                        buffering.setVisibility(View.VISIBLE);
                        bufferingLabel.setVisibility(View.VISIBLE);
                    } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
                        buffering.setVisibility(View.GONE);
                        bufferingLabel.setVisibility(View.GONE);
                    }
                    return false;
                }
            });
            mp.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
                @Override
                public void onVideoSizeChanged(MediaPlayer player, int width, int height) {
                    fitSurface(width, height);
                }
            });
            mp.prepareAsync();
        } catch (Exception e) {
            buffering.setVisibility(View.GONE);
            bufferingLabel.setVisibility(View.GONE);
            Toast.makeText(this, "Could not play " + displayName(c.name), Toast.LENGTH_SHORT).show();
        }
    }

    /** Self-heal: get a fresh HLS for this channel's /live page and resume. */
    private void resolveYouTubeAgain(final Channel c) {
        resolving = true;
        buffering.setVisibility(View.VISIBLE);
        bufferingLabel.setVisibility(View.VISIBLE);
        bufferingLabel.setText("Live link changed - finding the new stream...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String fresh = YtResolver.resolveHls(c.ytLiveUrl);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        resolving = false;
                        if (stopped) return;
                        if (fresh != null && fresh.length() > 0) {
                            c.url = fresh;
                            play(index);
                        } else {
                            nowPlaying.setText(displayName(c.name));
                            subInfo.setText("YouTube live is offline right now. Press Back and try the TV stream for this channel.");
                            panel.setVisibility(View.VISIBLE);
                            handler.removeCallbacks(hideOverlay);
                            buffering.setVisibility(View.GONE);
                            bufferingLabel.setVisibility(View.GONE);
                        }
                    }
                });
            }
        }).start();
    }

    private void fitSurface(int vw, int vh) {
        if (vw <= 0 || vh <= 0) return;
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        float scale = Math.min((float) screenW / vw, (float) screenH / vh);
        int w = (int) (vw * scale);
        int h = (int) (vh * scale);
        android.view.ViewGroup.LayoutParams lp = surface.getLayoutParams();
        lp.width = w;
        lp.height = h;
        surface.setLayoutParams(lp);
    }

    private void togglePause() {
        if (mp == null) return;
        try {
            if (mp.isPlaying()) {
                mp.pause();
                paused = true;
                showOverlay(0);
            } else {
                mp.start();
                paused = false;
                showOverlay(2500);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_CHANNEL_UP:
            case KeyEvent.KEYCODE_MEDIA_NEXT:
                play(index + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
            case KeyEvent.KEYCODE_CHANNEL_DOWN:
            case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                play(index - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                showOverlay(3500);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                togglePause();
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void releasePlayer() {
        if (mp != null) {
            try {
                mp.reset();
                mp.release();
            } catch (Exception ignored) {
            }
            mp = null;
        }
    }

    @Override
    protected void onDestroy() {
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        releasePlayer();
        super.onDestroy();
    }
}

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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PlayerActivity extends Activity {

    private List<Channel> list;
    private int index;
    private MediaPlayer mp;
    private SurfaceView surface;
    private SurfaceHolder holder;
    private ProgressBar buffering;
    private TextView nowPlaying;
    private TextView hint;
    private Handler handler = new Handler();
    private int errorStreak = 0;
    private boolean stopped = false;
    private boolean paused = false;

    private final Runnable hideOverlay = new Runnable() {
        @Override
        public void run() {
            if (!paused) {
                nowPlaying.setVisibility(View.GONE);
                hint.setVisibility(View.GONE);
            }
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
        nowPlaying = (TextView) findViewById(R.id.nowPlaying);
        hint = (TextView) findViewById(R.id.hint);
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

    private void play(int i) {
        if (list == null || list.isEmpty()) return;
        index = ((i % list.size()) + list.size()) % list.size();
        final Channel c = list.get(index);
        stopped = false;
        paused = false;
        releasePlayer();
        nowPlaying.setText(c.name);
        nowPlaying.setVisibility(View.VISIBLE);
        hint.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideOverlay);
        handler.postDelayed(hideOverlay, 4000);
        buffering.setVisibility(View.VISIBLE);
        try {
            mp = new MediaPlayer();
            mp.setDisplay(holder);
            mp.setDataSource(getApplicationContext(), Uri.parse(c.url), headersFor(c));
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer player) {
                    errorStreak = 0;
                    buffering.setVisibility(View.GONE);
                    player.start();
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer player, int what, int extra) {
                    buffering.setVisibility(View.GONE);
                    errorStreak++;
                    if (errorStreak >= 12) {
                        nowPlaying.setText(c.name + " — stream unavailable. Press ▲ for next channel.");
                        nowPlaying.setVisibility(View.VISIBLE);
                        return true;
                    }
                    Toast.makeText(PlayerActivity.this, "Couldn't play " + c.name + " — skipping", Toast.LENGTH_SHORT).show();
                    handler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!stopped) play(index + 1);
                        }
                    }, 700);
                    return true;
                }
            });
            mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
                @Override
                public boolean onInfo(MediaPlayer player, int what, int extra) {
                    if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) buffering.setVisibility(View.VISIBLE);
                    else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) buffering.setVisibility(View.GONE);
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
            Toast.makeText(this, "Couldn't play " + c.name, Toast.LENGTH_SHORT).show();
        }
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
                nowPlaying.setVisibility(View.VISIBLE);
                hint.setVisibility(View.VISIBLE);
                handler.removeCallbacks(hideOverlay);
            } else {
                mp.start();
                paused = false;
                handler.removeCallbacks(hideOverlay);
                handler.postDelayed(hideOverlay, 2500);
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

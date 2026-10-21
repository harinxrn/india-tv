package com.hari.indiatv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

/**
 * In-app YouTube player, v1.4.
 *
 * Error 153 ("Video player configuration error") is YouTube rejecting
 * embeds that arrive without a Referer/origin. So every attempt now sends
 * an explicit Referer header and an origin parameter:
 *  1. youtube.com/embed/ID loaded with Referer: https://www.youtube.com/
 *  2. youtube-nocookie.com/embed/ID, same treatment
 *  3. IFrame-API wrapper with playerVars.origin set
 * A JS probe watches the page for real playback vs. YouTube's error text
 * (including the numeric code). If all attempts fail, a dialog offers the
 * channel's IPTV stream or the YouTube app instead of stranding the user
 * on an error page.
 */
public class WebPlayerActivity extends Activity {

    public static final String EXTRA_VIDEO_ID = "videoId";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_ORIGINAL_URL = "originalUrl";

    private static final String PROBE_JS =
            "(function(){try{"
            + "var t=document.body?document.body.innerText:'';"
            + "var m=t.match(/Error (1[0-9][0-9])/);"
            + "if(m)return 'ERR:'+m[1];"
            + "if(document.querySelector('.ytp-error'))return 'ERR:ytp';"
            + "var v=document.querySelector('video');"
            + "if(v&&v.currentTime>0&&!v.paused)return 'PLAYING';"
            + "if(v)return 'LOADED:'+v.readyState;"
            + "return 'NONE';}catch(x){return 'NONE';}})()";

    private WebView web;
    private ProgressBar spinner;
    private TextView titleView;
    private String videoId;
    private String originalUrl;
    private int attempt = 0;
    private boolean settled = false;
    private String lastError = "";
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_webplayer);

        videoId = getIntent().getStringExtra(EXTRA_VIDEO_ID);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        originalUrl = getIntent().getStringExtra(EXTRA_ORIGINAL_URL);
        if (videoId == null) {
            finish();
            return;
        }
        spinner = (ProgressBar) findViewById(R.id.webSpinner);
        titleView = (TextView) findViewById(R.id.webTitle);
        titleView.setText(title == null ? "" : title);

        web = (WebView) findViewById(R.id.webview);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUserAgentString("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36");
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        startAttempt(0);
    }

    private void startAttempt(int n) {
        if (n > 2) {
            showFailureDialog();
            return;
        }
        attempt = n;
        settled = false;
        spinner.setVisibility(View.VISIBLE);
        if (n == 0) {
            Map<String, String> h = new HashMap<String, String>();
            h.put("Referer", "https://www.youtube.com/");
            web.loadUrl("https://www.youtube.com/embed/" + videoId
                    + "?autoplay=1&rel=0&playsinline=1&fs=1&origin=https://www.youtube.com", h);
            scheduleProbes();
        } else if (n == 1) {
            Map<String, String> h = new HashMap<String, String>();
            h.put("Referer", "https://www.youtube-nocookie.com/");
            web.loadUrl("https://www.youtube-nocookie.com/embed/" + videoId
                    + "?autoplay=1&rel=0&playsinline=1&fs=1&origin=https://www.youtube-nocookie.com", h);
            scheduleProbes();
        } else {
            web.loadDataWithBaseURL("https://www.youtube.com/", wrapperHtml(videoId), "text/html", "UTF-8", null);
        }
    }

    private void scheduleProbes() {
        final int thisAttempt = attempt;
        for (int delay = 3000; delay <= 12000; delay += 3000) {
            final boolean last = delay == 12000;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (settled || attempt != thisAttempt || web == null) return;
                    web.evaluateJavascript(PROBE_JS, new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String value) {
                            if (settled || attempt != thisAttempt) return;
                            if (value == null) value = "";
                            if (value.contains("PLAYING")) {
                                onPlaying();
                            } else if (value.contains("ERR:")) {
                                lastError = value.replace("\"", "").replace("ERR:", "");
                                startAttempt(thisAttempt + 1);
                            } else if (last) {
                                lastError = "timeout";
                                startAttempt(thisAttempt + 1);
                            }
                        }
                    });
                }
            }, delay);
        }
    }

    private void onPlaying() {
        settled = true;
        spinner.setVisibility(View.GONE);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                titleView.setVisibility(View.GONE);
            }
        }, 3000);
    }

    private void showFailureDialog() {
        settled = true;
        spinner.setVisibility(View.GONE);
        String msg = "YouTube won't play this channel inside the app"
                + (lastError.length() > 0 ? " (error " + lastError + ")" : "") + ".";
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("YouTube blocked in-app play")
                .setMessage(msg + "\n\nThe channel's IPTV stream still works:")
                .setPositiveButton("Play IPTV stream", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (AppData.playList != null && !AppData.playList.isEmpty()) {
                            startActivity(new Intent(WebPlayerActivity.this, PlayerActivity.class));
                        }
                        finish();
                    }
                })
                .setNeutralButton("Open YouTube app", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openOriginal();
                        finish();
                    }
                })
                .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                    }
                });
        b.show();
    }

    private String wrapperHtml(String vid) {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'>"
                + "<style>html,body{margin:0;padding:0;background:#000;height:100%}#p{position:absolute;top:0;left:0;width:100%;height:100%}</style>"
                + "</head><body><div id='p'></div>"
                + "<script src='https://www.youtube.com/iframe_api'></script>"
                + "<script>var player;"
                + "function onYouTubeIframeAPIReady(){player=new YT.Player('p',{videoId:'" + vid + "',"
                + "playerVars:{autoplay:1,rel:0,playsinline:1,fs:1,origin:'https://www.youtube.com'},"
                + "events:{onReady:function(e){e.target.playVideo();},"
                + "onStateChange:function(e){if(e.data==1){AndroidBridge.playing();}},"
                + "onError:function(e){AndroidBridge.error(e.data);}}});}"
                + "function togglePlay(){if(!player||!player.getPlayerState)return;var st=player.getPlayerState();if(st==1){player.pauseVideo();}else{player.playVideo();}}"
                + "</script></body></html>";
    }

    public class Bridge {
        @JavascriptInterface
        public void playing() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (attempt == 2 && !settled) onPlaying();
                }
            });
        }

        @JavascriptInterface
        public void error(final int code) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (settled) return;
                    lastError = String.valueOf(code);
                    startAttempt(3);
                }
            });
        }
    }

    private void openOriginal() {
        if (originalUrl == null) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(originalUrl));
            i.setPackage("com.google.android.youtube.tv");
            startActivity(i);
        } catch (ActivityNotFoundException e1) {
            try {
                Intent i2 = new Intent(Intent.ACTION_VIEW, Uri.parse(originalUrl));
                i2.setPackage("org.smarttube.stable");
                startActivity(i2);
            } catch (ActivityNotFoundException e2) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(originalUrl)));
                } catch (ActivityNotFoundException ignored) {
                }
            }
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
            if (web != null) {
                if (attempt == 2) {
                    web.evaluateJavascript("togglePlay()", null);
                } else {
                    web.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE));
                    web.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE));
                }
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}

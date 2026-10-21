package com.hari.indiatv;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ImageLoader {
    private static final LruCache<String, Bitmap> CACHE =
            new LruCache<String, Bitmap>(32 * 1024 * 1024) {
                @Override
                protected int sizeOf(String key, Bitmap value) {
                    return value.getByteCount();
                }
            };
    private static final ExecutorService POOL = Executors.newFixedThreadPool(4);

    public static void load(final ImageView view, final String url) {
        if (url == null || url.length() == 0) {
            view.setImageDrawable(null);
            view.setTag(null);
            return;
        }
        view.setTag(url);
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            view.setImageBitmap(cached);
            return;
        }
        view.setImageDrawable(null);
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = download(url);
                if (bmp != null) CACHE.put(url, bmp);
                final Bitmap result = bmp;
                view.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result != null && url.equals(view.getTag())) {
                            view.setImageBitmap(result);
                        }
                    }
                });
            }
        });
    }

    private static Bitmap download(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36");
            InputStream in = conn.getInputStream();
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            // Read stream twice: decode bounds first via a buffered copy is complex;
            // logos are small, so decode directly with a sample size of 1 capped by max dims.
            Bitmap bmp = BitmapFactory.decodeStream(in);
            in.close();
            if (bmp != null && bmp.getWidth() > 320) {
                int h = (int) (bmp.getHeight() * (320.0 / bmp.getWidth()));
                Bitmap scaled = Bitmap.createScaledBitmap(bmp, 320, Math.max(1, h), true);
                if (scaled != bmp) bmp.recycle();
                return scaled;
            }
            return bmp;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }
}

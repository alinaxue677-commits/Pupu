package com.pupu.pet;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

public class PetService extends Service {

    static volatile boolean running = false;

    private static final int FGS_TYPE_SPECIAL_USE = 1 << 30; // ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE

    private WindowManager wm;
    private WebView web;
    private WindowManager.LayoutParams lp;
    private final Handler main = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;

    // gesture state
    private float downRawX, downRawY, lastRawX;
    private long lastMoveTime;
    private int startX, startY;
    private boolean dragging, longPressing;
    private long lastTapTime = 0;
    private int touchSlop;
    private final Runnable longPress = () -> {
        longPressing = true;
        js("pet.longPressStart()");
        buzz(15);
    };

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        running = true;
        prefs = getSharedPreferences("pupu", MODE_PRIVATE);
        touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        startAsForeground();
        addPet();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            if ("stop".equals(intent.getAction())) {
                stopSelf();
            } else if ("mood".equals(intent.getAction())) {
                String m = intent.getStringExtra("mood");
                if (m != null && m.matches("[a-z]+")) js("pet.setMood('" + m + "')");
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (web != null) {
            try { wm.removeView(web); } catch (Exception ignored) { }
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }

    private void startAsForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel("pupu", "噗噗", NotificationManager.IMPORTANCE_MIN);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);

        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, PetService.class).setAction("stop"), PendingIntent.FLAG_IMMUTABLE);

        Notification n = new Notification.Builder(this, "pupu")
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("噗噗在桌面上")
                .setContentText("点这里换心情")
                .setContentIntent(open)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_stat), "收起噗噗", stop).build())
                .build();

        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, FGS_TYPE_SPECIAL_USE);
        else startForeground(1, n);
    }

    @SuppressLint({"SetJavaScriptEnabled", "ClickableViewAccessibility"})
    private void addPet() {
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float d = dm.density;
        int w = (int) (150 * d), h = (int) (190 * d);

        lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = prefs.getInt("x", dm.widthPixels - w);
        lp.y = prefs.getInt("y", (int) (dm.heightPixels * 0.55f));
        clamp();

        web = new WebView(this);
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setMediaPlaybackRequiresUserGesture(false);
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.addJavascriptInterface(new Bridge(), "Android");
        web.setOnTouchListener((v, e) -> onTouch(e));
        web.loadUrl("file:///android_asset/pet.html");

        wm.addView(web, lp);
    }

    private void clamp() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int maxX = Math.max(0, dm.widthPixels - lp.width);
        int maxY = Math.max(0, dm.heightPixels - lp.height);
        lp.x = Math.max(0, Math.min(maxX, lp.x));
        lp.y = Math.max(0, Math.min(maxY, lp.y));
    }

    private boolean onTouch(MotionEvent e) {
        float d = getResources().getDisplayMetrics().density;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downRawX = lastRawX = e.getRawX();
                downRawY = e.getRawY();
                lastMoveTime = SystemClock.uptimeMillis();
                startX = lp.x;
                startY = lp.y;
                dragging = false;
                longPressing = false;
                js("pet.touch()");
                main.postDelayed(longPress, 450);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float dx = e.getRawX() - downRawX, dy = e.getRawY() - downRawY;
                if (!dragging && Math.hypot(dx, dy) > touchSlop * 1.5) {
                    dragging = true;
                    main.removeCallbacks(longPress);
                    if (longPressing) { longPressing = false; js("pet.longPressEnd()"); }
                    js("pet.dragStart()");
                }
                if (dragging) {
                    lp.x = startX + Math.round(dx);
                    lp.y = startY + Math.round(dy);
                    clamp();
                    wm.updateViewLayout(web, lp);
                    long now = SystemClock.uptimeMillis();
                    float dt = Math.max(1, now - lastMoveTime) / 1000f;
                    float vx = (e.getRawX() - lastRawX) / d / dt;
                    lastRawX = e.getRawX();
                    lastMoveTime = now;
                    int maxY = Math.max(1, getResources().getDisplayMetrics().heightPixels - lp.height);
                    float height = 1f - (float) lp.y / maxY; // 0 = bottom of screen, 1 = top
                    js("pet.dragMove(" + Math.round(vx) + "," + String.format(java.util.Locale.US, "%.3f", height) + ")");
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                main.removeCallbacks(longPress);
                if (dragging) {
                    js("pet.dragEnd()");
                    prefs.edit().putInt("x", lp.x).putInt("y", lp.y).apply();
                } else if (longPressing) {
                    js("pet.longPressEnd()");
                } else if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                    long now = SystemClock.uptimeMillis();
                    if (now - lastTapTime < 320) {
                        js("pet.feed()");
                        lastTapTime = 0;
                    } else {
                        js("pet.puff()");
                        lastTapTime = now;
                    }
                }
                dragging = false;
                longPressing = false;
                return true;
        }
        return true;
    }

    private void js(String code) {
        main.post(() -> { if (web != null) web.evaluateJavascript("window.pet&&" + code, null); });
    }

    private void buzz(int ms) {
        try {
            Vibrator v = getSystemService(Vibrator.class);
            if (v != null && v.hasVibrator()) v.vibrate(VibrationEffect.createOneShot(ms, 60));
        } catch (Exception ignored) { }
    }

    private class Bridge {
        @JavascriptInterface public String getMood() { return prefs.getString("mood", "happy"); }
        @JavascriptInterface public int getEaten() { return prefs.getInt("eaten", 0); }
        @JavascriptInterface public void setEaten(int n) { prefs.edit().putInt("eaten", n).apply(); }
        @JavascriptInterface public boolean soundOn() {
            if (!prefs.getBoolean("sound", true)) return false;
            AudioManager am = getSystemService(AudioManager.class);
            return am == null || am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL;
        }
        @JavascriptInterface public void vibrate(int ms) { buzz(Math.max(5, Math.min(80, ms))); }
    }
}

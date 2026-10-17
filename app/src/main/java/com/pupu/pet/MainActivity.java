package com.pupu.pet;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final String[][] MOODS = {
            {"happy", "开心", "#FFB040"},
            {"shy", "害羞", "#FF96B6"},
            {"sleepy", "困", "#EC8C4A"},
            {"angry", "生气", "#FF5C2C"},
            {"cold", "冷", "#78BEFF"},
    };

    private final Button[] moodButtons = new Button[MOODS.length];
    private Button toggle;
    private TextView hint;
    private boolean waitingForPermission = false;

    private float dp(float v) { return v * getResources().getDisplayMetrics().density; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        getWindow().setStatusBarColor(Color.parseColor("#1D1A2B"));
        getWindow().setNavigationBarColor(Color.parseColor("#1D1A2B"));
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (waitingForPermission) {
            waitingForPermission = false;
            if (Settings.canDrawOverlays(this)) showPet();
            else Toast.makeText(this, "还没打开权限，噗噗出不来哦", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#1D1A2B"));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) dp(24);
        root.setPadding(pad, (int) dp(48), pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("噗噗");
        title.setTextSize(44);
        title.setTextColor(Color.parseColor("#FFA24A"));
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("一团有点歪的小火，住在你的手机桌面上。");
        sub.setTextSize(15);
        sub.setTextColor(Color.parseColor("#A99FBB"));
        sub.setPadding(0, (int) dp(4), 0, (int) dp(28));
        root.addView(sub);

        toggle = new Button(this);
        toggle.setAllCaps(false);
        toggle.setTextSize(17);
        toggle.setTextColor(Color.parseColor("#2A1A10"));
        toggle.setBackground(pill("#FFA24A", "#FFA24A"));
        toggle.setOnClickListener(v -> {
            if (PetService.running) hidePet();
            else showPet();
            v.postDelayed(this::refresh, 300);
        });
        root.addView(toggle, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) dp(54)));

        hint = new TextView(this);
        hint.setTextSize(13);
        hint.setTextColor(Color.parseColor("#A99FBB"));
        hint.setPadding(0, (int) dp(10), 0, (int) dp(28));
        root.addView(hint);

        TextView moodLabel = new TextView(this);
        moodLabel.setText("心情");
        moodLabel.setTextSize(13);
        moodLabel.setLetterSpacing(0.12f);
        moodLabel.setTextColor(Color.parseColor("#A99FBB"));
        moodLabel.setPadding(0, 0, 0, (int) dp(8));
        root.addView(moodLabel);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(row);
        for (int i = 0; i < MOODS.length; i++) {
            final String key = MOODS[i][0];
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(MOODS[i][1]);
            b.setTextSize(14);
            b.setTextColor(Color.parseColor("#F3E9DC"));
            b.setOnClickListener(v -> setMood(key));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, (int) dp(44), 1f);
            lp.setMargins((int) dp(3), 0, (int) dp(3), 0);
            row.addView(b, lp);
            moodButtons[i] = b;
        }

        TextView how = new TextView(this);
        how.setText("怎么和噗噗玩\n\n"
                + "点一下：它会“噗”地吐个烟圈\n"
                + "连点两下：喂它一根小木柴，吃多了会慢慢长大\n"
                + "长按：摸摸它，会害羞变粉\n"
                + "按住拖动：把它挪到桌面任何地方\n"
                + "一段时间不理它：会缩成小火种睡着，晚上睡得更快\n\n"
                + "想收起噗噗，可以回到这里，或者在通知栏点“收起噗噗”。");
        how.setTextSize(14);
        how.setLineSpacing(dp(3), 1f);
        how.setTextColor(Color.parseColor("#D8CDE4"));
        how.setPadding(0, (int) dp(32), 0, 0);
        root.addView(how);

        return scroll;
    }

    private GradientDrawable pill(String fill, String stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.parseColor(fill));
        g.setCornerRadius(dp(999));
        g.setStroke((int) dp(1.5f), Color.parseColor(stroke));
        return g;
    }

    private void refresh() {
        boolean on = PetService.running;
        toggle.setText(on ? "收起噗噗" : "让噗噗出来");
        hint.setText(on ? "噗噗已经在桌面上了，回到桌面看看吧。"
                : "第一次会请你打开“显示在其他应用上层”的权限，打开后返回这里就好。");
        String mood = prefs().getString("mood", "happy");
        for (int i = 0; i < MOODS.length; i++) {
            boolean sel = MOODS[i][0].equals(mood);
            moodButtons[i].setBackground(pill(sel ? "#3A2F4D" : "#262138", sel ? MOODS[i][2] : "#3A3350"));
        }
    }

    private SharedPreferences prefs() { return getSharedPreferences("pupu", MODE_PRIVATE); }

    private void showPet() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请找到“噗噗”，打开“显示在其他应用上层”", Toast.LENGTH_LONG).show();
            waitingForPermission = true;
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            }
            return;
        }
        startForegroundService(new Intent(this, PetService.class));
    }

    private void hidePet() {
        stopService(new Intent(this, PetService.class));
    }

    private void setMood(String mood) {
        prefs().edit().putString("mood", mood).apply();
        if (PetService.running) {
            startService(new Intent(this, PetService.class).setAction("mood").putExtra("mood", mood));
        }
        refresh();
    }
}

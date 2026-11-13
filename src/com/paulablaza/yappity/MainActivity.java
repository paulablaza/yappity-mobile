package com.paulablaza.yappity;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

// The one screen: Groq key, permissions, start/stop, style, language and the last yap.
public class MainActivity extends Activity {
    private static WeakReference<MainActivity> open = new WeakReference<>(null);

    private float dp;
    private LinearLayout page;
    private EditText keyField;
    private Button micBtn, notifBtn, bubbleBtn, startBtn, xiaomiBtn;
    private TextView status, lastText;

    static void refreshIfOpen() {
        final MainActivity a = open.get();
        if (a != null) a.runOnUiThread(new Runnable() { public void run() { a.refresh(); } });
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        dp = getResources().getDisplayMetrics().density;
        takeKeyFrom(getIntent());
        build();
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        takeKeyFrom(intent);
        refresh();
    }

    // For testing over adb: am start -n com.paulablaza.yappity/.MainActivity --es groq_key gsk_...
    private void takeKeyFrom(Intent i) {
        String k = i == null ? null : i.getStringExtra("groq_key");
        if (k != null && k.trim().length() > 0) Store.prefs(this).edit().putString("groq_key", k.trim()).apply();
    }

    @Override protected void onResume() {
        super.onResume();
        open = new WeakReference<>(this);
        refresh();
        if (Store.prefs(this).getBoolean("enabled", false) && ready() && !YapService.running())
            YapService.start(this, null);
    }

    @Override protected void onPause() {
        super.onPause();
        open = new WeakReference<>(null);
    }

    private boolean hasMic() { return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }

    private boolean hasNotif() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean ready() { return hasMic() && hasNotif() && !Store.key(this).isEmpty(); }

    // ---- UI ----

    private void build() {
        ScrollView scroll = new ScrollView(this);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        int pad = px(20);
        page.setPadding(pad, px(28), pad, px(40));
        scroll.addView(page);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public android.view.WindowInsets onApplyWindowInsets(View v, android.view.WindowInsets in) {
                v.setPadding(0, in.getSystemWindowInsetTop(), 0, in.getSystemWindowInsetBottom());
                return in;
            }
        });

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        head.addView(logo, new LinearLayout.LayoutParams(px(56), px(56)));
        TextView title = text("Yappity", 26, true);
        title.setPadding(px(12), 0, 0, 0);
        head.addView(title);
        page.addView(head);
        page.addView(text("Tap the bubble, yap, tap it again. The cleaned up text gets copied, so you just paste it. Your keyboard stays yours.", 15, false));

        heading("1. Your Groq key");
        page.addView(text("Yappity runs on Groq, and the key is free. Get one at console.groq.com/keys and paste it here.", 14, false));
        keyField = new EditText(this);
        keyField.setHint("gsk_...");
        keyField.setSingleLine(true);
        keyField.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        page.addView(keyField);
        LinearLayout keyRow = row();
        keyRow.addView(button("Save key", new View.OnClickListener() {
            public void onClick(View v) {
                String k = keyField.getText().toString().trim();
                if (k.length() > 0) {
                    Store.prefs(MainActivity.this).edit().putString("groq_key", k).apply();
                    keyField.setText("");
                    toast("Key saved");
                }
                refresh();
            }
        }));
        keyRow.addView(button("Get a free key", new View.OnClickListener() {
            public void onClick(View v) { openUrl("https://console.groq.com/keys"); }
        }));
        page.addView(keyRow);

        heading("2. Let Yappity in");
        micBtn = button("", new View.OnClickListener() {
            public void onClick(View v) { requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, 1); }
        });
        page.addView(micBtn);
        notifBtn = button("", new View.OnClickListener() {
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 2);
            }
        });
        page.addView(notifBtn);
        bubbleBtn = button("", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            }
        });
        page.addView(bubbleBtn);
        if ("xiaomi".equalsIgnoreCase(Build.MANUFACTURER) || "redmi".equalsIgnoreCase(Build.BRAND) || "poco".equalsIgnoreCase(Build.BRAND)) {
            page.addView(text("On Xiaomi, Redmi and POCO phones, also turn on \"Display pop-up windows while running in the background\" and Autostart, or the bubble can disappear.", 13, false));
            xiaomiBtn = button("Open Xiaomi app settings", new View.OnClickListener() {
                public void onClick(View v) { openXiaomiPermissions(); }
            });
            page.addView(xiaomiBtn);
        }

        heading("3. Start it");
        startBtn = button("", new View.OnClickListener() {
            public void onClick(View v) {
                if (YapService.running()) {
                    Store.prefs(MainActivity.this).edit().putBoolean("enabled", false).apply();
                    YapService.start(MainActivity.this, YapService.ACTION_QUIT);
                } else if (ready()) {
                    Store.prefs(MainActivity.this).edit().putBoolean("enabled", true).putBoolean("bubble", true).apply();
                    YapService.start(MainActivity.this, null);
                } else {
                    toast("Do steps 1 and 2 first");
                }
                v.postDelayed(new Runnable() { public void run() { refresh(); } }, 300);
            }
        });
        page.addView(startBtn);
        status = text("", 14, false);
        page.addView(status);
        page.addView(text("You can also add the Yap tile to your quick settings: pull down twice, tap the pencil, drag Yap in.", 13, false));

        heading("Style");
        page.addView(radios("style", Store.style(this),
            new String[] { "clean", "format", "organize", "raw" },
            new String[] { "Clean up: your words, minus the um's", "Clean up and format: lists and short paragraphs",
                "Organize: a long ramble becomes a clear message", "Raw: exactly what Whisper heard" }));

        heading("Language");
        page.addView(radios("language", Store.language(this),
            new String[] { "", "mixed-tl", "en", "tl" },
            new String[] { "Auto", "English + Tagalog (Taglish)", "English", "Tagalog" }));

        heading("Last yap");
        lastText = text("", 15, false);
        lastText.setTextIsSelectable(true);
        page.addView(lastText);
        LinearLayout lastRow = row();
        lastRow.addView(button("Copy again", new View.OnClickListener() {
            public void onClick(View v) {
                String t = Store.prefs(MainActivity.this).getString("last_text", "");
                if (t.length() == 0) return;
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Yappity", t));
                if (Build.VERSION.SDK_INT < 33) toast("Copied");
            }
        }));
        lastRow.addView(button("Send again", new View.OnClickListener() {
            public void onClick(View v) {
                if (YapService.running()) YapService.start(MainActivity.this, YapService.ACTION_RETRY);
                else toast("Start Yappity first");
            }
        }));
        page.addView(lastRow);

        TextView foot = text("Yappity " + BuildInfo.VERSION + ". Free and open source. Your recordings stay on this phone, only Groq hears them.", 12, false);
        foot.setPadding(0, px(24), 0, 0);
        foot.setAlpha(0.6f);
        page.addView(foot);
    }

    private void refresh() {
        if (page == null) return;
        boolean hasKey = !Store.key(this).isEmpty();
        keyField.setHint(hasKey ? "Key saved. Paste a new one to replace it." : "gsk_...");
        micBtn.setText(hasMic() ? "Microphone: allowed" : "Allow the microphone");
        micBtn.setEnabled(!hasMic());
        notifBtn.setText(hasNotif() ? "Notifications: allowed" : "Allow notifications (for the Yap button)");
        notifBtn.setEnabled(!hasNotif());
        boolean overlay = Settings.canDrawOverlays(this);
        bubbleBtn.setText(overlay ? "Floating bubble: allowed" : "Allow the floating bubble");
        bubbleBtn.setEnabled(!overlay);
        startBtn.setText(YapService.running() ? "Stop Yappity" : "Start Yappity");
        String s;
        if (!YapService.running()) s = ready() ? "Not running." : "Do steps 1 and 2, then start it.";
        else {
            switch (YapService.currentState()) {
                case YapService.LISTENING: s = "Listening..."; break;
                case YapService.WORKING: s = "Writing it down..."; break;
                default: s = overlay ? "Running. Tap the bubble or the Yap button in your notifications." : "Running. Use the Yap button in your notifications.";
            }
        }
        status.setText(s);
        String last = Store.prefs(this).getString("last_text", "");
        lastText.setText(last.length() > 0 ? last : "Nothing yet.");
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        refresh();
    }

    private void openXiaomiPermissions() {
        try {
            Intent i = new Intent("miui.intent.action.APP_PERM_EDITOR");
            i.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
            i.putExtra("extra_pkgname", getPackageName());
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    // ---- Small helpers ----

    private RadioGroup radios(final String pref, String current, final String[] ids, String[] labels) {
        RadioGroup g = new RadioGroup(this);
        for (int i = 0; i < ids.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setText(labels[i]);
            r.setId(View.generateViewId());
            r.setTag(ids[i]);
            r.setPadding(px(4), px(6), 0, px(6));
            g.addView(r);
            if (ids[i].equals(current)) r.setChecked(true);
        }
        g.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            public void onCheckedChanged(RadioGroup group, int id) {
                View r = group.findViewById(id);
                if (r != null) Store.prefs(MainActivity.this).edit().putString(pref, (String) r.getTag()).apply();
            }
        });
        return g;
    }

    private void heading(String s) {
        TextView t = text(s, 18, true);
        t.setPadding(0, px(24), 0, px(6));
        page.addView(t);
    }

    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, px(4), 0, px(4));
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int px(int v) { return (int) (v * dp); }
}

package com.paulablaza.yappity;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

// The one screen: start/stop, setup checklist, style, language and the last yap.
public class MainActivity extends Activity {
    private static WeakReference<MainActivity> open = new WeakReference<>(null);

    private LinearLayout page;
    private TextView statusChip, startBtn, startHint, lastText;
    private LinearLayout setupCard, styleCard, langCard;

    static void refreshIfOpen() {
        final MainActivity a = open.get();
        if (a != null) a.runOnUiThread(new Runnable() { public void run() { a.refresh(); } });
    }

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Ui.init(this);
        takeKeyFrom(getIntent());
        getWindow().setStatusBarColor(Ui.bg);
        getWindow().setNavigationBarColor(Ui.bg);
        if (!Ui.dark) getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | (Build.VERSION.SDK_INT >= 26 ? View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR : 0));
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
        if (Store.prefs(this).getBoolean("enabled", false) && ready()) {
            YapService.start(this, null);
            page.postDelayed(new Runnable() { public void run() { refresh(); } }, 400);
        }
    }

    @Override protected void onPause() {
        super.onPause();
        open = new WeakReference<>(null);
    }

    @Override public void onRequestPermissionsResult(int code, String[] perms, int[] results) { refresh(); }

    private boolean hasKey() { return !Store.key(this).isEmpty(); }
    private boolean hasMic() { return checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED; }
    private boolean hasNotif() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
    private boolean hasOverlay() { return Settings.canDrawOverlays(this); }
    private boolean ready() { return hasKey() && hasMic() && hasNotif(); }
    private static boolean isXiaomi() {
        return "xiaomi".equalsIgnoreCase(Build.MANUFACTURER) || "redmi".equalsIgnoreCase(Build.BRAND) || "poco".equalsIgnoreCase(Build.BRAND);
    }

    // ---- Layout ----

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.bg);
        scroll.setFillViewport(true);
        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(px(18), px(12), px(18), px(32));
        scroll.addView(page);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                v.setPadding(0, in.getSystemWindowInsetTop(), 0, in.getSystemWindowInsetBottom());
                return in;
            }
        });

        // Header
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(0, px(8), 0, px(16));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.logo);
        head.addView(logo, new LinearLayout.LayoutParams(px(44), px(44)));
        TextView name = label("Yappity", 22, Ui.text, true);
        name.setPadding(px(10), 0, 0, 0);
        head.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        statusChip = label("", 12.5f, Ui.muted, true);
        statusChip.setPadding(px(12), px(6), px(12), px(6));
        head.addView(statusChip);
        page.addView(head);

        // Hero
        LinearLayout hero = card();
        hero.addView(label("Voice typing that works in any app", 18, Ui.text, true));
        TextView sub = label("Tap Yappy-chan on the edge of your screen, talk, then tap done. The cleaned up text is copied, so you just paste it. Your keyboard stays the same.", 14, Ui.muted, false);
        sub.setPadding(0, px(6), 0, px(16));
        sub.setLineSpacing(0, 1.15f);
        hero.addView(sub);
        startBtn = label("", 16, 0xFFFFFFFF, true);
        startBtn.setGravity(Gravity.CENTER);
        startBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { startOrStop(); } });
        hero.addView(startBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(52)));
        startHint = label("", 12.5f, Ui.muted, false);
        startHint.setGravity(Gravity.CENTER);
        startHint.setPadding(0, px(10), 0, 0);
        hero.addView(startHint);
        page.addView(hero);

        section("Setup");
        setupCard = card();
        setupCard.setPadding(0, px(4), 0, px(4));
        page.addView(setupCard);

        section("Style");
        styleCard = card();
        styleCard.setPadding(0, px(4), 0, px(4));
        page.addView(styleCard);

        section("Language");
        langCard = card();
        langCard.setPadding(0, px(4), 0, px(4));
        page.addView(langCard);

        section("Last yap");
        LinearLayout last = card();
        lastText = label("", 15, Ui.text, false);
        lastText.setTextIsSelectable(true);
        lastText.setLineSpacing(0, 1.15f);
        last.addView(lastText);
        LinearLayout lastRow = new LinearLayout(this);
        lastRow.setPadding(0, px(12), 0, 0);
        lastRow.addView(chipButton("Copy again", new View.OnClickListener() {
            public void onClick(View v) {
                String t = Store.prefs(MainActivity.this).getString("last_text", "");
                if (t.length() == 0) return;
                ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Yappity", t));
                if (Build.VERSION.SDK_INT < 33) toast("Copied");
            }
        }));
        View gap = new View(this);
        lastRow.addView(gap, new LinearLayout.LayoutParams(px(8), 1));
        lastRow.addView(chipButton("Send the recording again", new View.OnClickListener() {
            public void onClick(View v) {
                if (YapService.running()) YapService.start(MainActivity.this, YapService.ACTION_RETRY);
                else toast("Start Yappity first");
            }
        }));
        last.addView(lastRow);
        page.addView(last);

        TextView tip = label("Tip: add the Yap tile to Quick Settings. Pull down twice, tap the pencil, and drag Yap in.", 12.5f, Ui.muted, false);
        tip.setPadding(px(4), px(18), px(4), 0);
        page.addView(tip);
        TextView foot = label("Yappity " + BuildInfo.VERSION + ", free and open source. Recordings stay on this phone. Only Groq hears them, to write them down.", 12, Ui.muted, false);
        foot.setPadding(px(4), px(8), px(4), 0);
        foot.setAlpha(0.8f);
        page.addView(foot);
    }

    private void refresh() {
        if (page == null) return;
        boolean running = YapService.running();

        statusChip.setText(running ? "●  On" : "●  Off");
        statusChip.setTextColor(running ? Ui.GOOD_DEEP : Ui.muted);
        statusChip.setBackground(Ui.rounded(Ui.card, px(20), Ui.border, px(1)));

        startBtn.setText(running ? "Turn off Yappity" : "Start Yappity");
        if (running) {
            startBtn.setTextColor(Ui.text);
            startBtn.setBackground(ripple(Ui.rounded(Ui.chip, px(14), Ui.border, px(1))));
        } else {
            startBtn.setTextColor(0xFFFFFFFF);
            startBtn.setBackground(ripple(Ui.rounded(ready() ? Ui.ACCENT : 0xFFA78BFA, px(14), 0, 0)));
        }
        String hint;
        if (running) {
            switch (YapService.currentState()) {
                case YapService.LISTENING: hint = "Listening..."; break;
                case YapService.WORKING: hint = "Writing it down..."; break;
                default: hint = hasOverlay() ? "Yappy-chan is on the edge of your screen. Drag her up or down to move her." : "Use the Yap button in your notifications.";
            }
        } else {
            hint = ready() ? "Ready when you are." : "Finish the setup below first.";
        }
        startHint.setText(hint);

        setupCard.removeAllViews();
        setupRow("Groq key", hasKey() ? "Saved. Tap to change it." : "Free. Yappity uses it to write down what you say.", hasKey(), "Add", new View.OnClickListener() {
            public void onClick(View v) { askKey(); }
        }, true);
        setupRow("Microphone", "So Yappity can hear you.", hasMic(), "Allow", new View.OnClickListener() {
            public void onClick(View v) { requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, 1); }
        }, false);
        setupRow("Notifications", "Shows the Yap button while Yappity is on.", hasNotif(), "Allow", new View.OnClickListener() {
            public void onClick(View v) {
                if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[] { Manifest.permission.POST_NOTIFICATIONS }, 2);
            }
        }, false);
        setupRow("Floating bubble", "Lets Yappy-chan sit on top of other apps.", hasOverlay(), "Allow", new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            }
        }, false);
        if (isXiaomi()) {
            setupRow("Xiaomi extras", "Turn on \"Display pop-up windows while running in the background\" and Autostart, or she can vanish.", false, "Open", new View.OnClickListener() {
                public void onClick(View v) { openXiaomiPermissions(); }
            }, false);
        }

        styleCard.removeAllViews();
        choices(styleCard, "style", Store.style(this),
            new String[] { "clean", "format", "organize", "raw" },
            new String[] { "Clean up", "Clean up and format", "Organize", "Raw" },
            new String[] { "Your words, minus the um's and false starts.", "Same, plus lists and short paragraphs.",
                "A long ramble becomes a clear message.", "Exactly what Whisper heard." });

        langCard.removeAllViews();
        choices(langCard, "language", Store.language(this),
            new String[] { "", "mixed-tl", "en", "tl" },
            new String[] { "Auto", "English + Tagalog", "English", "Tagalog" },
            new String[] { "Figures out the language each time.", "For Taglish. Keeps every word the way you said it.", null, null });

        String last = Store.prefs(this).getString("last_text", "");
        lastText.setText(last.length() > 0 ? last : "Nothing yet. Your last yap shows up here.");
        lastText.setTextColor(last.length() > 0 ? Ui.text : Ui.muted);
    }

    private void startOrStop() {
        if (YapService.running()) {
            Store.prefs(this).edit().putBoolean("enabled", false).apply();
            YapService.start(this, YapService.ACTION_QUIT);
        } else if (ready()) {
            Store.prefs(this).edit().putBoolean("enabled", true).putBoolean("bubble", true).apply();
            YapService.start(this, null);
        } else {
            toast("Finish the setup first");
        }
        page.postDelayed(new Runnable() { public void run() { refresh(); } }, 300);
    }

    private void askKey() {
        final EditText field = new EditText(this);
        field.setHint("gsk_...");
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(px(20), px(8), px(20), 0);
        wrap.addView(field);
        new AlertDialog.Builder(this)
            .setTitle("Your Groq key")
            .setMessage("It's free. Make one at console.groq.com/keys, copy it, and paste it here.")
            .setView(wrap)
            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) {
                    String k = field.getText().toString().trim();
                    if (k.length() > 0) {
                        Store.prefs(MainActivity.this).edit().putString("groq_key", k).apply();
                        toast("Key saved");
                    }
                    refresh();
                }
            })
            .setNeutralButton("Get a key", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface d, int w) { openUrl("https://console.groq.com/keys"); }
            })
            .setNegativeButton("Cancel", null)
            .show();
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

    // ---- Pieces ----

    private void setupRow(String title, String desc, boolean done, String action, View.OnClickListener l, boolean tapWhenDone) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(px(16), px(12), px(16), px(12));
        TextView mark = label(done ? "✓" : "", 14, 0xFFFFFFFF, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(done ? Ui.rounded(Ui.GOOD_DEEP, px(12), 0, 0) : Ui.rounded(0, px(12), Ui.border, px(2)));
        row.addView(mark, new LinearLayout.LayoutParams(px(24), px(24)));
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(px(14), 0, px(10), 0);
        texts.addView(label(title, 15, Ui.text, true));
        TextView d = label(desc, 13, Ui.muted, false);
        d.setPadding(0, px(2), 0, 0);
        texts.addView(d);
        row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (!done) {
            TextView b = label(action, 13.5f, 0xFFFFFFFF, true);
            b.setGravity(Gravity.CENTER);
            b.setPadding(px(16), px(8), px(16), px(8));
            b.setBackground(ripple(Ui.rounded(Ui.ACCENT, px(18), 0, 0)));
            b.setOnClickListener(l);
            row.addView(b);
        } else if (tapWhenDone) {
            row.setBackground(ripple(null));
            row.setOnClickListener(l);
        }
        if (setupCard.getChildCount() > 0) setupCard.addView(divider());
        setupCard.addView(row);
    }

    private void choices(final LinearLayout card, final String pref, String current, final String[] ids, String[] titles, String[] descs) {
        for (int i = 0; i < ids.length; i++) {
            final String id = ids[i];
            boolean on = id.equals(current);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(px(16), px(12), px(16), px(12));
            row.setBackground(ripple(null));
            TextView dot = new TextView(this);
            dot.setBackground(on ? Ui.rounded(Ui.ACCENT, px(11), Ui.ACCENT_SOFT, px(5)) : Ui.rounded(0, px(11), Ui.border, px(2)));
            row.addView(dot, new LinearLayout.LayoutParams(px(22), px(22)));
            LinearLayout texts = new LinearLayout(this);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setPadding(px(14), 0, 0, 0);
            texts.addView(label(titles[i], 15, Ui.text, on));
            if (descs[i] != null) {
                TextView d = label(descs[i], 13, Ui.muted, false);
                d.setPadding(0, px(2), 0, 0);
                texts.addView(d);
            }
            row.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            row.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Store.prefs(MainActivity.this).edit().putString(pref, id).apply();
                    refresh();
                }
            });
            if (i > 0) card.addView(divider());
            card.addView(row);
        }
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(px(18), px(18), px(18), px(18));
        c.setBackground(Ui.rounded(Ui.card, px(18), Ui.border, px(1)));
        return c;
    }

    private void section(String s) {
        TextView t = label(s.toUpperCase(), 12, Ui.muted, true);
        t.setLetterSpacing(0.08f);
        t.setPadding(px(6), px(24), 0, px(8));
        page.addView(t);
    }

    private View divider() {
        View v = new View(this);
        v.setBackgroundColor(Ui.border);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, px(1) / 2 + 1));
        p.setMarginStart(px(54));
        v.setLayoutParams(p);
        return v;
    }

    private TextView chipButton(String s, View.OnClickListener l) {
        TextView b = label(s, 13.5f, Ui.text, true);
        b.setPadding(px(14), px(9), px(14), px(9));
        b.setBackground(ripple(Ui.rounded(Ui.chip, px(18), Ui.border, px(1))));
        b.setOnClickListener(l);
        return b;
    }

    private RippleDrawable ripple(android.graphics.drawable.Drawable content) {
        return new RippleDrawable(ColorStateList.valueOf(0x33888888), content,
            content == null ? new android.graphics.drawable.ColorDrawable(0xFFFFFFFF) : null);
    }

    private TextView label(String s, float sp, int color, boolean medium) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (medium) t.setTypeface(Ui.MEDIUM);
        return t;
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception ignored) { }
    }

    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    private int px(float v) { return (int) (v * getResources().getDisplayMetrics().density); }
}

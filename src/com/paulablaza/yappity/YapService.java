package com.paulablaza.yappity;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.service.quicksettings.TileService;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;

// Keeps Yappity alive with a notification, shows the floating bubble, records, sends to Groq
// and copies the result. Runs as a microphone foreground service so the mic works over other apps.
public class YapService extends Service {
    static final String ACTION_TOGGLE = "toggle";
    static final String ACTION_CANCEL = "cancel";
    static final String ACTION_RETRY = "retry";
    static final String ACTION_BUBBLE = "bubble";
    static final String ACTION_QUIT = "quit";

    static final int IDLE = 0, LISTENING = 1, WORKING = 2, DONE = 3, OOPS = 4;

    static volatile YapService instance;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private ImageView bubble;
    private WindowManager.LayoutParams lp;
    private Recorder recorder;
    private int state = IDLE;
    private int frame;
    private String message = "";

    static boolean running() { return instance != null; }
    static int currentState() { YapService s = instance; return s == null ? IDLE : s.state; }

    static void start(Context c, String action) {
        Intent i = new Intent(c, YapService.class);
        if (action != null) i.setAction(action);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        instance = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        NotificationManager nm = getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel("yappity", "Yappity", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Keeps the bubble and the Yap button ready");
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(1, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        else
            startForeground(1, buildNotification());
        if (Store.prefs(this).getBoolean("bubble", true)) showBubble();
        updateNotification();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String a = intent == null ? null : intent.getAction();
        if (ACTION_TOGGLE.equals(a)) toggle();
        else if (ACTION_CANCEL.equals(a)) cancel();
        else if (ACTION_RETRY.equals(a)) retry();
        else if (ACTION_BUBBLE.equals(a)) {
            boolean on = bubble == null;
            Store.prefs(this).edit().putBoolean("bubble", on).apply();
            if (on) showBubble(); else hideBubble();
            updateNotification();
        } else if (ACTION_QUIT.equals(a)) {
            cancel();
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (recorder != null) recorder.cancel();
        hideBubble();
        ui.removeCallbacksAndMessages(null);
        instance = null;
        TileService.requestListeningState(this, new android.content.ComponentName(this, YapTile.class));
        super.onDestroy();
    }

    // ---- Recording flow ----

    void toggle() {
        if (state == LISTENING) finish();
        else if (state != WORKING) listen();
    }

    private void listen() {
        if (Store.key(this).isEmpty()) {
            oops("Add your free Groq key in Yappity first.");
            openApp();
            return;
        }
        recorder = new Recorder();
        if (!recorder.start()) {
            recorder = null;
            oops("Yappity can't use the mic. Open Yappity and allow it.");
            return;
        }
        buzz();
        setState(LISTENING, "Listening. Tap again when you're done.");
        ui.postDelayed(watchFull, 1000);
    }

    private final Runnable watchFull = new Runnable() {
        public void run() {
            if (state != LISTENING || recorder == null) return;
            if (recorder.isFull()) finish(); else ui.postDelayed(this, 1000);
        }
    };

    private void cancel() {
        if (recorder != null) { recorder.cancel(); recorder = null; }
        if (state == LISTENING) setState(IDLE, "");
    }

    private void finish() {
        if (recorder == null) return;
        final byte[] wav = recorder.stop();
        recorder = null;
        buzz();
        if (Recorder.seconds(wav) < 0.4) { setState(IDLE, ""); return; }
        final File saved = Store.save(this, wav);
        process(wav, saved);
    }

    private void retry() {
        if (state == LISTENING || state == WORKING) return;
        File f = Store.latest(this);
        if (f == null) { oops("There's no saved yap to send again."); return; }
        try { process(Store.read(f), f); }
        catch (Exception e) { oops("Couldn't open the saved yap."); }
    }

    private void process(final byte[] wav, final File saved) {
        setState(WORKING, "Writing it down");
        final Context ctx = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                String key = Store.key(ctx);
                try {
                    String raw = Groq.transcribe(key, "whisper-large-v3", wav, Store.language(ctx));
                    if (isNothing(raw, Recorder.seconds(wav))) {
                        ui.post(new Runnable() { public void run() { oops("Didn't catch anything. Try again a bit closer to the mic."); } });
                        return;
                    }
                    String style = Store.style(ctx);
                    String text = "raw".equals(style) ? raw : Groq.cleanup(key, raw, style);
                    final String done = tidy(text);
                    Store.prefs(ctx).edit().putString("last_text", done).putString("last_raw", raw).apply();
                    ui.post(new Runnable() { public void run() { deliver(done); } });
                } catch (final Exception e) {
                    Log.w("process: " + e);
                    ui.post(new Runnable() { public void run() { oops(Groq.friendly(e)); } });
                }
            }
        }, "yap-groq").start();
    }

    private void deliver(String text) {
        try {
            ClipboardManager cb = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            cb.setPrimaryClip(ClipData.newPlainText("Yappity", text));
        } catch (Exception e) {
            Log.w("clipboard: " + e);
            oops("Android blocked the copy. Open Yappity to copy it.");
            return;
        }
        // Android 13 and newer already shows its own "Copied" popup.
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, "Copied. Long-press to paste.", Toast.LENGTH_SHORT).show();
        setState(DONE, "Copied. Paste it anywhere.");
        ui.postDelayed(new Runnable() { public void run() { if (state == DONE) setState(IDLE, ""); } }, 1800);
    }

    private void oops(String why) {
        Toast.makeText(this, why, Toast.LENGTH_LONG).show();
        setState(OOPS, why);
        ui.postDelayed(new Runnable() { public void run() { if (state == OOPS) setState(IDLE, ""); } }, 3000);
    }

    private static final String[] PHANTOMS = {
        "", ".", "you", "thank you", "thank you.", "thanks.", "thanks for watching!", "thanks for watching.",
        "thank you for watching.", "thank you for watching!", "bye.", "bye!", "okay.", "so"
    };

    static boolean isNothing(String raw, double seconds) {
        String t = raw == null ? "" : raw.trim().toLowerCase();
        if (t.isEmpty()) return true;
        if (seconds >= 3) return false;
        for (String p : PHANTOMS) if (p.equals(t)) return true;
        return false;
    }

    static String tidy(String text) {
        if (text == null) return "";
        String t = text.replace(" — ", ", ").replace(" – ", ", ").replace("—", ", ").replace("–", "-");
        while (t.contains(" ,")) t = t.replace(" ,", ",");
        while (t.contains(",,")) t = t.replace(",,", ",");
        while (t.contains("  ")) t = t.replace("  ", " ");
        t = t.replace("\r\n", "\n");
        while (t.contains("\n\n\n")) t = t.replace("\n\n\n", "\n\n");
        return t.trim();
    }

    private void setState(int s, String msg) {
        state = s;
        message = msg;
        frame = 0;
        ui.removeCallbacks(animate);
        drawBubble();
        if (s == LISTENING || s == WORKING || s == DONE) ui.postDelayed(animate, 160);
        updateNotification();
        TileService.requestListeningState(this, new android.content.ComponentName(this, YapTile.class));
        MainActivity.refreshIfOpen();
    }

    private void buzz() {
        if (bubble != null) bubble.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
    }

    private void openApp() {
        Intent i = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try { startActivity(i); } catch (Exception ignored) { }
    }

    // ---- Bubble ----

    private static final int[] TALK = { R.drawable.talk_0, R.drawable.talk_1, R.drawable.talk_2, R.drawable.talk_3 };
    private static final int[] TIDY = { R.drawable.tidy_0, R.drawable.tidy_1, R.drawable.tidy_2, R.drawable.tidy_3 };
    private static final int[] DONE_FRAMES = { R.drawable.done_0, R.drawable.done_1, R.drawable.done_2, R.drawable.done_3 };

    private final Runnable animate = new Runnable() {
        public void run() {
            frame++;
            drawBubble();
            if (state == LISTENING || state == WORKING || state == DONE) ui.postDelayed(this, 160);
        }
    };

    private void drawBubble() {
        if (bubble == null) return;
        int res;
        switch (state) {
            case LISTENING: res = TALK[frame % 4]; break;
            case WORKING: res = TIDY[frame % 4]; break;
            case DONE: res = DONE_FRAMES[Math.min(frame, 3)]; break;
            case OOPS: res = R.drawable.oops; break;
            default: res = R.drawable.base;
        }
        bubble.setImageResource(res);
        bubble.setAlpha(state == IDLE ? 0.8f : 1f);
    }

    private void showBubble() {
        if (bubble != null || !Settings.canDrawOverlays(this)) return;
        float dp = getResources().getDisplayMetrics().density;
        int size = (int) (60 * dp);
        bubble = new ImageView(this);
        bubble.setScaleType(ImageView.ScaleType.FIT_CENTER);
        bubble.setContentDescription("Yappity. Tap to yap.");
        lp = new WindowManager.LayoutParams(size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = Store.prefs(this).getInt("bubble_x", getResources().getDisplayMetrics().widthPixels - size);
        lp.y = Store.prefs(this).getInt("bubble_y", (int) (getResources().getDisplayMetrics().heightPixels * 0.4));
        bubble.setOnTouchListener(new BubbleTouch((int) (8 * dp)));
        try { wm.addView(bubble, lp); }
        catch (Exception e) { Log.w("bubble: " + e); bubble = null; return; }
        drawBubble();
    }

    private void hideBubble() {
        if (bubble == null) return;
        try { wm.removeView(bubble); } catch (Exception ignored) { }
        bubble = null;
    }

    boolean bubbleShown() { return bubble != null; }

    // Tap to yap, drag to move, long-press to open Yappity.
    private class BubbleTouch implements View.OnTouchListener {
        final int slop;
        float downX, downY;
        int startX, startY;
        boolean dragging, longPressed;
        final Runnable longPress = new Runnable() {
            public void run() {
                longPressed = true;
                buzz();
                openApp();
            }
        };

        BubbleTouch(int slop) { this.slop = slop; }

        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = e.getRawX(); downY = e.getRawY();
                    startX = lp.x; startY = lp.y;
                    dragging = false; longPressed = false;
                    ui.postDelayed(longPress, 600);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = e.getRawX() - downX, dy = e.getRawY() - downY;
                    if (!dragging && Math.hypot(dx, dy) > slop) { dragging = true; ui.removeCallbacks(longPress); }
                    if (dragging) {
                        lp.x = startX + (int) dx;
                        lp.y = startY + (int) dy;
                        try { wm.updateViewLayout(bubble, lp); } catch (Exception ignored) { }
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    ui.removeCallbacks(longPress);
                    if (dragging) {
                        Store.prefs(YapService.this).edit().putInt("bubble_x", lp.x).putInt("bubble_y", lp.y).apply();
                    } else if (!longPressed) {
                        toggle();
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    ui.removeCallbacks(longPress);
                    return true;
            }
            return false;
        }
    }

    // ---- Notification ----

    private PendingIntent action(String a, int req) {
        Intent i = new Intent(this, YapService.class).setAction(a);
        return PendingIntent.getService(this, req, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        String title;
        switch (state) {
            case LISTENING: title = "Listening"; break;
            case WORKING: title = "Writing it down"; break;
            case DONE: title = "Copied"; break;
            case OOPS: title = "Oops"; break;
            default: title = "Ready to yap";
        }
        String text = message.length() > 0 ? message : "Tap Yap, talk, then tap Done. The text gets copied.";
        Notification.Builder b = new Notification.Builder(this, "yappity")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openPi)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false);
        if (state == LISTENING) {
            b.addAction(new Notification.Action.Builder(null, "Done", action(ACTION_TOGGLE, 1)).build());
            b.addAction(new Notification.Action.Builder(null, "Cancel", action(ACTION_CANCEL, 2)).build());
        } else {
            b.addAction(new Notification.Action.Builder(null, "Yap", action(ACTION_TOGGLE, 1)).build());
            b.addAction(new Notification.Action.Builder(null, bubble == null ? "Show bubble" : "Hide bubble", action(ACTION_BUBBLE, 3)).build());
            b.addAction(new Notification.Action.Builder(null, "Quit", action(ACTION_QUIT, 4)).build());
        }
        return b.build();
    }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        try { nm.notify(1, buildNotification()); } catch (Exception ignored) { }
    }
}

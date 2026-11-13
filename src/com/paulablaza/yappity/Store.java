package com.paulablaza.yappity;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;

// Settings and saved recordings. Every yap is written to disk before it goes to Groq.
final class Store {
    static final int KEEP_RECORDINGS = 30;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("yappity", Context.MODE_PRIVATE); }

    static String key(Context c) { return prefs(c).getString("groq_key", "").trim(); }
    static String style(Context c) { return prefs(c).getString("style", "clean"); }
    static String language(Context c) { return prefs(c).getString("language", ""); }

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "recordings");
        d.mkdirs();
        return d;
    }

    static File save(Context c, byte[] wav) {
        String name = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".wav";
        File f = new File(dir(c), name);
        try (FileOutputStream out = new FileOutputStream(f)) { out.write(wav); }
        catch (Exception e) { Log.w("save: " + e); }
        prune(c);
        return f;
    }

    static File latest(Context c) {
        File[] files = dir(c).listFiles();
        if (files == null || files.length == 0) return null;
        Arrays.sort(files);
        return files[files.length - 1];
    }

    static byte[] read(File f) throws Exception {
        try (RandomAccessFile r = new RandomAccessFile(f, "r")) {
            byte[] b = new byte[(int) r.length()];
            r.readFully(b);
            return b;
        }
    }

    static void prune(Context c) {
        File[] files = dir(c).listFiles();
        if (files == null || files.length <= KEEP_RECORDINGS) return;
        Arrays.sort(files);
        for (int i = 0; i < files.length - KEEP_RECORDINGS; i++) files[i].delete();
    }
}

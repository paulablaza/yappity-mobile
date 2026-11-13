package com.paulablaza.yappity;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.ByteArrayOutputStream;

// Mic capture, 16 kHz mono 16-bit, the same format Yappity on Windows sends to Whisper.
final class Recorder {
    static final int RATE = 16000;
    static final int MAX_SECONDS = 600; // stays well under Groq's 25 MB upload limit

    private AudioRecord rec;
    private Thread thread;
    private volatile boolean running;
    private final ByteArrayOutputStream pcm = new ByteArrayOutputStream();

    boolean start() {
        int min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        try {
            rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min, RATE));
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) { release(); return false; }
            rec.startRecording();
        } catch (Exception e) {
            Log.w("mic: " + e);
            release();
            return false;
        }
        pcm.reset();
        running = true;
        thread = new Thread(new Runnable() {
            public void run() {
                byte[] buf = new byte[3200];
                long max = (long) RATE * 2 * MAX_SECONDS;
                while (running) {
                    int n = rec.read(buf, 0, buf.length);
                    if (n > 0) synchronized (pcm) { pcm.write(buf, 0, n); }
                    if (pcm.size() >= max) running = false;
                }
            }
        }, "yap-mic");
        thread.start();
        return true;
    }

    boolean isFull() { return !running && rec != null; }

    // Stops and returns the recording as a WAV file in memory.
    byte[] stop() {
        running = false;
        try { if (thread != null) thread.join(1000); } catch (InterruptedException ignored) { }
        release();
        byte[] data;
        synchronized (pcm) { data = pcm.toByteArray(); }
        return wav(data);
    }

    void cancel() { stop(); }

    static double seconds(byte[] wav) { return Math.max(0, wav.length - 44) / (RATE * 2.0); }

    private void release() {
        if (rec != null) {
            try { rec.stop(); } catch (Exception ignored) { }
            rec.release();
            rec = null;
        }
    }

    static byte[] wav(byte[] pcm) {
        byte[] out = new byte[44 + pcm.length];
        put(out, 0, "RIFF"); le32(out, 4, 36 + pcm.length); put(out, 8, "WAVE");
        put(out, 12, "fmt "); le32(out, 16, 16); le16(out, 20, 1); le16(out, 22, 1);
        le32(out, 24, RATE); le32(out, 28, RATE * 2); le16(out, 32, 2); le16(out, 34, 16);
        put(out, 36, "data"); le32(out, 40, pcm.length);
        System.arraycopy(pcm, 0, out, 44, pcm.length);
        return out;
    }

    private static void put(byte[] b, int at, String s) { for (int i = 0; i < 4; i++) b[at + i] = (byte) s.charAt(i); }
    private static void le32(byte[] b, int at, int v) { for (int i = 0; i < 4; i++) b[at + i] = (byte) (v >> (8 * i)); }
    private static void le16(byte[] b, int at, int v) { b[at] = (byte) v; b[at + 1] = (byte) (v >> 8); }
}

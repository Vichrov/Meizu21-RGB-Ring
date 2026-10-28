package com.vichrov.meizu21rgbring;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;

import java.lang.reflect.Method;

/**
 * Reproduces the music-light preview playback path recovered from Meizu Settings 12.1.0.
 *
 * Stock Settings uses RingtoneExt.play(..., "light_preview_music", STREAM_MUSIC, ...),
 * with AudioAttributes CONTENT_TYPE_MUSIC, USAGE_MEDIA, flags=64 and the vendor tag
 * "light_preview_music". Flyme's system-side music-light component observes that tagged
 * playback and renders the real multi-segment ring animation.
 */
final class MusicPreviewController {
    private static final int SAMPLE_RATE = 44100;
    private static final String PREVIEW_TAG = "light_preview_music";

    private AudioTrack track;
    private Thread thread;
    private volatile boolean stopRequested;

    synchronized String start() {
        stopLocked();
        try {
            AudioAttributes.Builder ab = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setFlags(64);

            // addTag is a Flyme/hidden framework method on this build. UserService runs under
            // Shizuku shell so hidden-API reflection is available, just like the other ring code.
            Method addTag = AudioAttributes.Builder.class.getDeclaredMethod("addTag", String.class);
            addTag.setAccessible(true);
            addTag.invoke(ab, PREVIEW_TAG);
            AudioAttributes attributes = ab.build();

            AudioFormat format = new AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build();

            int minimum = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            if (minimum <= 0) minimum = 4096;
            int bufferBytes = Math.max(minimum * 2, 8192);

            track = new AudioTrack(attributes, format, bufferBytes,
                    AudioTrack.MODE_STREAM, AudioManager.AUDIO_SESSION_ID_GENERATE);
            if (track.getState() != AudioTrack.STATE_INITIALIZED) {
                stopLocked();
                return "MUSIC PREVIEW ERROR: AudioTrack did not initialize";
            }

            stopRequested = false;
            AudioTrack localTrack = track;
            thread = new Thread(() -> runTone(localTrack), "flyme-music-light-preview");
            thread.setDaemon(true);
            localTrack.play();
            thread.start();
            return "MUSIC PREVIEW STARTED: tag=" + PREVIEW_TAG
                    + " content=MUSIC usage=MEDIA flags=64, pulsing tone ~12s";
        } catch (Throwable t) {
            stopLocked();
            return "MUSIC PREVIEW ERROR: " + rootMessage(t);
        }
    }

    synchronized String stop() {
        stopLocked();
        return "MUSIC PREVIEW STOP OK";
    }

    synchronized String diagnostics() {
        try {
            Method m = AudioAttributes.Builder.class.getDeclaredMethod("addTag", String.class);
            return "Music preview path: READY\n"
                    + "AudioAttributes.Builder.addTag = " + m + "\n"
                    + "tag = " + PREVIEW_TAG + "\n"
                    + "contentType = MUSIC(2), usage = MEDIA(1), flags = 64, stream = MUSIC(3)";
        } catch (Throwable t) {
            return "Music preview path: ERROR: " + rootMessage(t);
        }
    }

    private void runTone(AudioTrack localTrack) {
        final int chunk = 1024;
        short[] pcm = new short[chunk];
        long frame = 0;
        long maxFrames = SAMPLE_RATE * 12L;
        try {
            while (!stopRequested && frame < maxFrames) {
                for (int i = 0; i < chunk; i++, frame++) {
                    double t = frame / (double) SAMPLE_RATE;
                    // Slow envelope plus quicker beats makes the visualizer input obvious.
                    double slow = 0.35 + 0.65 * (0.5 + 0.5 * Math.sin(2.0 * Math.PI * 0.35 * t));
                    double beatPhase = (t * 2.1) % 1.0;
                    double beat = beatPhase < 0.18 ? (1.0 - beatPhase / 0.18) : 0.08;
                    double envelope = Math.min(1.0, 0.12 + 0.72 * slow + 0.45 * beat);
                    double sample = Math.sin(2.0 * Math.PI * 440.0 * t)
                            + 0.35 * Math.sin(2.0 * Math.PI * 660.0 * t);
                    int value = (int) Math.round(sample * envelope * 9000.0);
                    value = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
                    pcm[i] = (short) value;
                }
                int written = localTrack.write(pcm, 0, pcm.length, AudioTrack.WRITE_BLOCKING);
                if (written < 0) break;
            }
        } catch (Throwable ignored) {
        } finally {
            synchronized (this) {
                if (track == localTrack) {
                    try { localTrack.pause(); } catch (Throwable ignored) {}
                    try { localTrack.flush(); } catch (Throwable ignored) {}
                    try { localTrack.stop(); } catch (Throwable ignored) {}
                    try { localTrack.release(); } catch (Throwable ignored) {}
                    track = null;
                    thread = null;
                }
            }
        }
    }

    private void stopLocked() {
        stopRequested = true;
        AudioTrack t = track;
        track = null;
        thread = null;
        if (t != null) {
            try { t.pause(); } catch (Throwable ignored) {}
            try { t.flush(); } catch (Throwable ignored) {}
            try { t.stop(); } catch (Throwable ignored) {}
            try { t.release(); } catch (Throwable ignored) {}
        }
    }

    private static String rootMessage(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null && x.getCause() != x) x = x.getCause();
        return x.getClass().getSimpleName() + ": " + String.valueOf(x.getMessage());
    }
}

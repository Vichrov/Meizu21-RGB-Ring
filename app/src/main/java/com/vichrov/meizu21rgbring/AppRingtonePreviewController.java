package com.vichrov.meizu21rgbring;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;

import java.lang.reflect.Method;

/**
 * Reproduces the exact stock Settings music-light preview path recovered from
 * Meizu Settings 12.1.0 RingtoneExt and MusicLightSettingsFragment bytecode.
 */
final class AppRingtonePreviewController {
    private static final String PREVIEW_TAG = "light_preview_music";
    private static final String STOCK_PREVIEW_URI =
            "content://media/internal/audio/media/104?title=East%20ville%20memory&canonical=1";

    private final Context context;
    private final AudioManager audioManager;
    private final AudioManager.OnAudioFocusChangeListener focusListener = focusChange -> { };
    private Ringtone ringtone;

    AppRingtonePreviewController(Context context) {
        this.context = context.getApplicationContext();
        this.audioManager = (AudioManager) this.context.getSystemService(Context.AUDIO_SERVICE);
    }

    synchronized String start() {
        stopLocked();
        try {
            Uri uri = Uri.parse(STOCK_PREVIEW_URI);
            Ringtone r = RingtoneManager.getRingtone(context, uri);
            if (r == null) {
                return "STOCK RINGTONE PREVIEW ERROR: RingtoneManager returned null for " + STOCK_PREVIEW_URI;
            }

            AudioAttributes.Builder ab = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setFlags(64);

            Method addTag = AudioAttributes.Builder.class.getDeclaredMethod("addTag", String.class);
            addTag.setAccessible(true);
            addTag.invoke(ab, PREVIEW_TAG);
            AudioAttributes attributes = ab.build();

            r.setAudioAttributes(attributes);

            if (audioManager != null) {
                audioManager.requestAudioFocus(
                        focusListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            }

            ringtone = r;
            r.play();

            return "STOCK RINGTONE PREVIEW STARTED: uri=" + STOCK_PREVIEW_URI
                    + " tag=" + PREVIEW_TAG
                    + " content=MUSIC usage=MEDIA flags=64 stream=MUSIC"
                    + " isPlaying=" + safeIsPlaying(r);
        } catch (Throwable t) {
            stopLocked();
            return "STOCK RINGTONE PREVIEW ERROR: " + rootMessage(t);
        }
    }

    synchronized String stop() {
        stopLocked();
        return "STOCK RINGTONE PREVIEW STOP OK";
    }

    synchronized String diagnostics() {
        try {
            Method addTag = AudioAttributes.Builder.class.getDeclaredMethod("addTag", String.class);
            return "Exact stock RingtoneExt path READY\n"
                    + "URI = " + STOCK_PREVIEW_URI + "\n"
                    + "AudioAttributes.Builder.addTag = " + addTag + "\n"
                    + "tag = " + PREVIEW_TAG + "\n"
                    + "content=MUSIC(2), usage=MEDIA(1), flags=64, stream=MUSIC(3)";
        } catch (Throwable t) {
            return "Exact stock RingtoneExt path ERROR: " + rootMessage(t);
        }
    }

    private void stopLocked() {
        Ringtone r = ringtone;
        ringtone = null;
        if (r != null) {
            try { if (r.isPlaying()) r.stop(); } catch (Throwable ignored) { }
        }
        if (audioManager != null) {
            try { audioManager.abandonAudioFocus(focusListener); } catch (Throwable ignored) { }
        }
    }

    private static boolean safeIsPlaying(Ringtone r) {
        try { return r.isPlaying(); } catch (Throwable ignored) { return false; }
    }

    private static String rootMessage(Throwable t) {
        Throwable x = t;
        while (x.getCause() != null && x.getCause() != x) x = x.getCause();
        return x.getClass().getSimpleName() + ": " + String.valueOf(x.getMessage());
    }
}

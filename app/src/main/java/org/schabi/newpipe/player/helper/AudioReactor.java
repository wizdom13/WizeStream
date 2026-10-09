package org.schabi.newpipe.player.helper;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.audiofx.AudioEffect;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.media.AudioFocusRequestCompat;
import androidx.media.AudioManagerCompat;

import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.analytics.AnalyticsListener;

import java.util.function.Consumer;

public class AudioReactor implements AudioManager.OnAudioFocusChangeListener, AnalyticsListener {

    private static final String TAG = "AudioFocusReactor";

    private static final int DUCK_DURATION = 1500;
    private static final float DUCK_AUDIO_TO = .2f;

    private static final int FOCUS_GAIN_TYPE = AudioManagerCompat.AUDIOFOCUS_GAIN;
    private static final int STREAM_TYPE = AudioManager.STREAM_MUSIC;

    private final ExoPlayer player;
    private final Context context;
    private final AudioManager audioManager;

    private final AudioFocusRequestCompat request;
    private final Consumer<Float> volumeMultiplierConsumer;
    private ValueAnimator audioAnimator;

    public AudioReactor(@NonNull final Context context,
                        @NonNull final ExoPlayer player,
                        @NonNull final Consumer<Float> volumeMultiplierConsumer) {
        this.player = player;
        this.volumeMultiplierConsumer = volumeMultiplierConsumer;
        this.context = context;
        this.audioManager = ContextCompat.getSystemService(context, AudioManager.class);
        player.addAnalyticsListener(this);

        request = new AudioFocusRequestCompat.Builder(FOCUS_GAIN_TYPE)
                //.setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(true)
                .setOnAudioFocusChangeListener(this)
                .build();
    }

    public void dispose() {
        cancelAudioAnimation();
        abandonAudioFocus();
        player.removeAnalyticsListener(this);
        notifyAudioSessionUpdate(false, player.getAudioSessionId());
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Audio Manager
    //////////////////////////////////////////////////////////////////////////*/

    public void requestAudioFocus() {
        AudioManagerCompat.requestAudioFocus(audioManager, request);
    }

    public void abandonAudioFocus() {
        AudioManagerCompat.abandonAudioFocusRequest(audioManager, request);
    }

    public int getVolume() {
        return audioManager.getStreamVolume(STREAM_TYPE);
    }

    public void setVolume(final int volume) {
        audioManager.setStreamVolume(STREAM_TYPE, volume, 0);
    }

    public int getMaxVolume() {
        return AudioManagerCompat.getStreamMaxVolume(audioManager, STREAM_TYPE);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // AudioFocus
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAudioFocusChange(final int focusChange) {
        Log.d(TAG, "onAudioFocusChange() called with: focusChange = [" + focusChange + "]");
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                onAudioFocusGain();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                onAudioFocusLossCanDuck();
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                onAudioFocusLoss();
                break;
        }
    }

    private void onAudioFocusGain() {
        Log.d(TAG, "onAudioFocusGain() called");
        cancelAudioAnimation();
        volumeMultiplierConsumer.accept(DUCK_AUDIO_TO);
        animateAudio(DUCK_AUDIO_TO, 1.0f);

        if (PlayerHelper.isResumeAfterAudioFocusGain(context)) {
            player.play();
        }
    }

    private void onAudioFocusLoss() {
        Log.d(TAG, "onAudioFocusLoss() called");
        cancelAudioAnimation();
        player.pause();
    }

    private void onAudioFocusLossCanDuck() {
        Log.d(TAG, "onAudioFocusLossCanDuck() called");
        cancelAudioAnimation();
        // Reduce the current effective volume while another app has audio focus.
        volumeMultiplierConsumer.accept(DUCK_AUDIO_TO);
    }

    private void animateAudio(final float from, final float to) {
        final ValueAnimator valueAnimator = new ValueAnimator();
        audioAnimator = valueAnimator;
        valueAnimator.setFloatValues(from, to);
        valueAnimator.setDuration(AudioReactor.DUCK_DURATION);
        valueAnimator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled;

            @Override
            public void onAnimationStart(final Animator animation) {
                volumeMultiplierConsumer.accept(from);
            }

            @Override
            public void onAnimationCancel(final Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                if (!cancelled) {
                    volumeMultiplierConsumer.accept(to);
                }
            }
        });
        valueAnimator.addUpdateListener(animation ->
                volumeMultiplierConsumer.accept(((float) animation.getAnimatedValue())));
        valueAnimator.start();
    }

    private void cancelAudioAnimation() {
        if (audioAnimator != null) {
            audioAnimator.cancel();
            audioAnimator = null;
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Audio Processing
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onAudioSessionIdChanged(@NonNull final EventTime eventTime,
                                        final int audioSessionId) {
        notifyAudioSessionUpdate(true, audioSessionId);
    }
    private void notifyAudioSessionUpdate(final boolean active, final int audioSessionId) {
        final Intent intent = new Intent(active
                ? AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION
                : AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION);
        intent.putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId);
        intent.putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.getPackageName());
        context.sendBroadcast(intent);
    }
}

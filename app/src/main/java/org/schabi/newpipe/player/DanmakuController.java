/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-FileCopyrightText: 2026 PipePipe contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.player;

import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.PlayerBinding;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsExtractor;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfo;
import org.schabi.newpipe.extractor.bulletComments.BulletCommentsInfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

/**
 * Loads and schedules danmaku/bullet comments for one video-player UI.
 *
 * Extraction runs only when the user enables danmaku. Live extractors are disconnected while
 * playback is paused so the app does not keep live-chat connections active unnecessarily.
 */
public final class DanmakuController {
    private static final String TAG = "DanmakuController";
    private static final long TICK_MILLIS = 100L;
    private static final long SEEK_RESET_THRESHOLD_MILLIS = 1_500L;

    private final Player player;
    private final PlayerBinding binding;

    @Nullable
    private Disposable loadDisposable;
    @Nullable
    private Disposable tickerDisposable;
    @Nullable
    private Disposable reconnectDisposable;
    @Nullable
    private BulletCommentsExtractor extractor;
    @NonNull
    private List<BulletCommentsInfoItem> comments = Collections.emptyList();

    private boolean supported;
    private boolean loaded;
    private long lastPositionMillis;
    private int nextCommentIndex;
    @Nullable
    private StreamInfo currentInfo;

    public DanmakuController(@NonNull final Player player,
                             @NonNull final PlayerBinding binding) {
        this.player = player;
        this.binding = binding;
        binding.danmakuToggle.setVisibility(View.GONE);
        binding.danmakuOverlay.setVisibility(View.GONE);
    }

    public void onMetadataChanged(@NonNull final StreamInfo info) {
        final boolean sameItem = currentInfo != null
                && currentInfo.getServiceId() == info.getServiceId()
                && currentInfo.getUrl().equals(info.getUrl());
        currentInfo = info;
        supported = serviceSupportsDanmaku(info.getServiceId());
        binding.danmakuToggle.setVisibility(supported ? View.VISIBLE : View.GONE);
        updateToggleIcon();

        if (!sameItem) {
            resetExtraction();
        }

        if (supported && isEnabled()) {
            binding.danmakuOverlay.setVisibility(View.VISIBLE);
            ensureLoaded();
        } else {
            binding.danmakuOverlay.setVisibility(View.GONE);
            binding.danmakuOverlay.clearComments();
        }
    }

    public void toggle() {
        if (!supported) {
            return;
        }
        final boolean enabled = !isEnabled();
        player.getPrefs().edit()
                .putBoolean(player.getContext().getString(R.string.danmaku_enabled_key), enabled)
                .apply();
        updateToggleIcon();

        if (enabled) {
            binding.danmakuOverlay.setVisibility(View.VISIBLE);
            ensureLoaded();
            if (player.isPlaying()) {
                start();
            }
        } else {
            stopTicker();
            disconnectLiveExtractor();
            binding.danmakuOverlay.clearComments();
            binding.danmakuOverlay.setVisibility(View.GONE);
        }
    }

    public void start() {
        if (!supported || !isEnabled()) {
            return;
        }
        binding.danmakuOverlay.setVisibility(View.VISIBLE);
        if (!loaded) {
            ensureLoaded();
            return;
        }
        if (tickerDisposable != null) {
            binding.danmakuOverlay.resumeComments();
            return;
        }

        reconnectLiveExtractor();
        seekToCurrentPosition();
        binding.danmakuOverlay.resumeComments();

        tickerDisposable = Observable.interval(
                        0L,
                        TICK_MILLIS,
                        TimeUnit.MILLISECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        ignored -> tick(),
                        error -> Log.e(TAG, "Danmaku scheduler failed", error));
    }

    /** Pause drawing and release live-comment connections. */
    public void pause() {
        stopTicker();
        binding.danmakuOverlay.pauseComments();
        disconnectLiveExtractor();
    }

    /** Pause visual advancement while buffering without tearing down the live extractor. */
    public void suspendForBuffering() {
        stopTicker();
        binding.danmakuOverlay.pauseComments();
    }

    public void reset() {
        currentInfo = null;
        supported = false;
        binding.danmakuToggle.setVisibility(View.GONE);
        binding.danmakuOverlay.setVisibility(View.GONE);
        resetExtraction();
    }

    public void destroy() {
        reset();
    }

    private void ensureLoaded() {
        final StreamInfo info = currentInfo;
        if (info == null || loaded || loadDisposable != null || !supported || !isEnabled()) {
            return;
        }

        loadDisposable = Single.fromCallable(() ->
                        BulletCommentsInfo.getInfo(
                                NewPipe.getService(info.getServiceId()),
                                info.getUrl()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        bulletInfo -> {
                            loadDisposable = null;
                            if (currentInfo == null
                                    || currentInfo.getServiceId() != info.getServiceId()
                                    || !currentInfo.getUrl().equals(info.getUrl())) {
                                bulletInfo.getBulletCommentsExtractor().disconnect();
                                return;
                            }

                            extractor = bulletInfo.getBulletCommentsExtractor();
                            final List<BulletCommentsInfoItem> loadedComments =
                                    new ArrayList<>(bulletInfo.getRelatedItems());
                            Collections.sort(loadedComments);
                            comments = loadedComments;
                            loaded = true;
                            seekToCurrentPosition();

                            if (player.isPlaying() && isEnabled()) {
                                start();
                            } else {
                                disconnectLiveExtractor();
                            }
                        },
                        error -> {
                            loadDisposable = null;
                            loaded = false;
                            Log.w(TAG, "Could not load danmaku comments", error);
                        });
    }

    private void tick() {
        if (!loaded || player.exoPlayerIsNull() || !isEnabled()) {
            return;
        }

        final long currentPosition = Math.max(0L, player.getExoPlayer().getCurrentPosition());
        final BulletCommentsExtractor currentExtractor = extractor;

        if (currentExtractor != null && currentExtractor.isLive()) {
            try {
                currentExtractor.setCurrentPlayPosition(currentPosition);
                final List<BulletCommentsInfoItem> liveMessages =
                        currentExtractor.getLiveMessages();
                if (liveMessages != null && !liveMessages.isEmpty()) {
                    binding.danmakuOverlay.showComments(liveMessages);
                }
            } catch (final Exception error) {
                Log.w(TAG, "Could not read live danmaku messages", error);
            }
            lastPositionMillis = currentPosition;
            return;
        }

        if (currentPosition < lastPositionMillis
                || currentPosition - lastPositionMillis > SEEK_RESET_THRESHOLD_MILLIS) {
            binding.danmakuOverlay.clearComments();
            seekToPosition(currentPosition);
            return;
        }

        final long drawUntil = currentPosition + TICK_MILLIS;
        final List<BulletCommentsInfoItem> due = new ArrayList<>();
        while (nextCommentIndex < comments.size()) {
            final BulletCommentsInfoItem item = comments.get(nextCommentIndex);
            if (item.getDuration() == null) {
                nextCommentIndex++;
                continue;
            }
            final long itemMillis = item.getDuration().toMillis();
            if (itemMillis > drawUntil) {
                break;
            }
            if (itemMillis >= lastPositionMillis) {
                due.add(item);
            }
            nextCommentIndex++;
        }

        if (!due.isEmpty()) {
            binding.danmakuOverlay.showComments(due);
        }
        lastPositionMillis = currentPosition;
    }

    private void seekToCurrentPosition() {
        if (player.exoPlayerIsNull()) {
            lastPositionMillis = 0L;
            nextCommentIndex = 0;
            return;
        }
        seekToPosition(Math.max(0L, player.getExoPlayer().getCurrentPosition()));
    }

    private void seekToPosition(final long positionMillis) {
        lastPositionMillis = positionMillis;
        nextCommentIndex = firstCommentAtOrAfter(comments, positionMillis);
    }

    static int firstCommentAtOrAfter(@NonNull final List<BulletCommentsInfoItem> items,
                                     final long positionMillis) {
        int low = 0;
        int high = items.size();
        while (low < high) {
            final int mid = (low + high) >>> 1;
            final BulletCommentsInfoItem item = items.get(mid);
            final long itemMillis = item.getDuration() == null
                    ? Long.MIN_VALUE
                    : item.getDuration().toMillis();
            if (itemMillis < positionMillis) {
                low = mid + 1;
            } else {
                high = mid;
            }
        }
        return low;
    }

    private void resetExtraction() {
        stopTicker();
        cancelReconnect();
        if (loadDisposable != null) {
            loadDisposable.dispose();
            loadDisposable = null;
        }
        disconnectLiveExtractor();
        extractor = null;
        comments = Collections.emptyList();
        loaded = false;
        nextCommentIndex = 0;
        lastPositionMillis = 0L;
        binding.danmakuOverlay.clearComments();
    }

    private void stopTicker() {
        if (tickerDisposable != null) {
            tickerDisposable.dispose();
            tickerDisposable = null;
        }
    }

    private void reconnectLiveExtractor() {
        final BulletCommentsExtractor currentExtractor = extractor;
        if (currentExtractor == null || !currentExtractor.isLive()
                || reconnectDisposable != null) {
            return;
        }

        reconnectDisposable = Schedulers.io().scheduleDirect(() -> {
            try {
                currentExtractor.reconnect();
            } catch (final Exception error) {
                Log.w(TAG, "Could not reconnect live danmaku extractor", error);
            } finally {
                reconnectDisposable = null;
            }
        });
    }

    private void cancelReconnect() {
        if (reconnectDisposable != null) {
            reconnectDisposable.dispose();
            reconnectDisposable = null;
        }
    }

    private void disconnectLiveExtractor() {
        cancelReconnect();
        if (extractor != null && extractor.isLive()) {
            try {
                extractor.disconnect();
            } catch (final Exception error) {
                Log.w(TAG, "Could not disconnect live danmaku extractor", error);
            }
        }
    }

    private boolean isEnabled() {
        return player.getPrefs().getBoolean(
                player.getContext().getString(R.string.danmaku_enabled_key),
                false);
    }

    private void updateToggleIcon() {
        binding.danmakuToggle.setImageResource(
                isEnabled()
                        ? R.drawable.ic_danmaku_enabled
                        : R.drawable.ic_danmaku_disabled);
        binding.danmakuToggle.setContentDescription(player.getContext().getString(
                isEnabled()
                        ? R.string.danmaku_disable
                        : R.string.danmaku_enable));
    }

    private boolean serviceSupportsDanmaku(final int serviceId) {
        try {
            return NewPipe.getService(serviceId)
                    .getServiceInfo()
                    .getMediaCapabilities()
                    .contains(StreamingService.ServiceInfo.MediaCapability.BULLET_COMMENTS);
        } catch (final Exception error) {
            Log.w(TAG, "Could not inspect danmaku capability", error);
            return false;
        }
    }
}

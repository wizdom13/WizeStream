package org.schabi.newpipe.local.holder;

import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.ktx.ViewUtils;
import org.schabi.newpipe.local.LocalItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.DependentPreferenceHelper;
import org.schabi.newpipe.util.Localization;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.StreamTypeUtil;
import org.schabi.newpipe.util.image.CoilHelper;
import org.schabi.newpipe.views.AnimatedProgressBar;

import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class LocalPlaylistStreamItemHolder extends LocalItemHolder {
    public final ImageView itemThumbnailView;
    public final TextView itemVideoTitleView;
    private final ImageView itemUploaderAvatarView;
    private final TextView itemUploaderView;
    private final TextView itemAdditionalDetailsView;
    public final TextView itemDurationView;
    private final TextView itemMembersOnlyView;
    private final View itemHandleView;
    private final AnimatedProgressBar itemProgressView;

    LocalPlaylistStreamItemHolder(final LocalItemBuilder infoItemBuilder, final int layoutId,
                                  final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemVideoTitleView = itemView.findViewById(R.id.itemVideoTitleView);
        itemUploaderAvatarView = itemView.findViewById(R.id.itemUploaderAvatarView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
        itemAdditionalDetailsView = itemView.findViewById(R.id.itemAdditionalDetails);
        itemDurationView = itemView.findViewById(R.id.itemDurationView);
        itemMembersOnlyView = itemView.findViewById(R.id.itemMembersOnlyView);
        itemHandleView = itemView.findViewById(R.id.itemHandle);
        itemProgressView = itemView.findViewById(R.id.itemProgressView);
    }

    public LocalPlaylistStreamItemHolder(final LocalItemBuilder infoItemBuilder,
                                         final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_stream_playlist_item, parent);
    }

    @Override
    public void updateFromItem(final LocalItem localItem,
                               final HistoryRecordManager historyRecordManager,
                               final DateTimeFormatter dateTimeFormatter) {
        if (!(localItem instanceof PlaylistStreamEntry)) {
            return;
        }
        final PlaylistStreamEntry item = (PlaylistStreamEntry) localItem;

        itemVideoTitleView.setText(item.getStreamEntity().getTitle());
        itemUploaderView.setText(item.getStreamEntity().getUploader());
        itemMembersOnlyView.setVisibility(item.getStreamEntity().getRequiresMembership()
                ? View.VISIBLE : View.GONE);
        CoilHelper.INSTANCE.loadAvatar(itemUploaderAvatarView,
                item.getStreamEntity().getUploaderAvatarUrl());

        final String sourceName = item.getStreamEntity().isLocalMedia()
                ? itemBuilder.getContext().getString(R.string.local_media_on_device)
                : ServiceHelper.getNameOfServiceById(item.getStreamEntity().getServiceId());
        itemAdditionalDetailsView.setText(getStreamInfoDetailLine(item, sourceName));

        if (item.getStreamEntity().getDuration() > 0) {
            itemDurationView.setText(Localization
                    .getDurationString(item.getStreamEntity().getDuration()));
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);

            if (DependentPreferenceHelper.getPositionsInListsEnabled(itemProgressView.getContext())
                    && item.getProgressMillis() > 0) {
                itemProgressView.setVisibility(View.VISIBLE);
                itemProgressView.setMax((int) item.getStreamEntity().getDuration());
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(item.getProgressMillis()));
            } else {
                itemProgressView.setVisibility(View.GONE);
            }
        } else if (StreamTypeUtil.isLiveStream(item.getStreamEntity().getStreamType())) {
            itemDurationView.setText(R.string.duration_live);
            itemDurationView.setBackgroundColor(ContextCompat.getColor(itemBuilder.getContext(),
                    R.color.live_duration_background_color));
            itemDurationView.setVisibility(View.VISIBLE);
            itemProgressView.setVisibility(View.GONE);
        } else {
            itemDurationView.setVisibility(View.GONE);
            itemProgressView.setVisibility(View.GONE);
        }
        updateDurationMarginForProgress();

        // Default thumbnail is shown on error, while loading and if the url is empty
        CoilHelper.INSTANCE.loadThumbnail(itemThumbnailView,
                item.getStreamEntity().getThumbnailUrl());

        itemView.setOnClickListener(view -> {
            if (itemBuilder.getOnItemSelectedListener() != null) {
                itemBuilder.getOnItemSelectedListener().selected(item);
            }
        });

        itemView.setLongClickable(true);
        itemView.setOnLongClickListener(view -> {
            if (itemBuilder.getOnItemSelectedListener() != null) {
                itemBuilder.getOnItemSelectedListener().held(item);
            }
            return true;
        });

        itemHandleView.setOnTouchListener(getOnTouchListener(item));
    }

    private String getStreamInfoDetailLine(final PlaylistStreamEntry item,
                                           final String sourceName) {
        if (item.getStreamEntity().isLocalMedia()) {
            return sourceName;
        }

        String viewsAndDate = "";
        final Long viewCount = item.getStreamEntity().getViewCount();
        if (viewCount != null && viewCount >= 0) {
            final StreamType streamType = item.getStreamEntity().getStreamType();
            if (streamType == StreamType.AUDIO_LIVE_STREAM) {
                viewsAndDate = Localization.listeningCount(itemBuilder.getContext(), viewCount);
            } else if (streamType == StreamType.LIVE_STREAM) {
                viewsAndDate = Localization.shortWatchingCount(itemBuilder.getContext(), viewCount);
            } else {
                viewsAndDate = Localization.shortViewCount(itemBuilder.getContext(), viewCount);
            }
        }

        final String uploadDate;
        if (item.getStreamEntity().getUploadDate() != null) {
            uploadDate = Localization.relativeTime(item.getStreamEntity().getUploadDate());
        } else {
            uploadDate = item.getStreamEntity().getTextualUploadDate();
        }

        final String details = Localization.concatenateStrings(viewsAndDate, uploadDate);
        return details.isEmpty() ? sourceName : details;
    }

    private void updateDurationMarginForProgress() {
        updateDurationMarginForProgress(itemProgressView.getVisibility() == View.VISIBLE
                && itemProgressView.getProgress() > 0);
    }

    private void updateDurationMarginForProgress(final boolean shouldRaiseDuration) {
        final ViewGroup.LayoutParams layoutParams = itemDurationView.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }

        final int bottomMargin = itemDurationView.getResources().getDimensionPixelSize(
                shouldRaiseDuration
                        ? R.dimen.stream_thumbnail_duration_margin_with_progress
                        : R.dimen.video_item_search_duration_margin);
        final ViewGroup.MarginLayoutParams marginLayoutParams =
                (ViewGroup.MarginLayoutParams) layoutParams;
        if (marginLayoutParams.bottomMargin != bottomMargin) {
            marginLayoutParams.bottomMargin = bottomMargin;
            itemDurationView.setLayoutParams(marginLayoutParams);
            itemDurationView.requestLayout();
        }
    }

    @Override
    public void updateState(final LocalItem localItem,
                            final HistoryRecordManager historyRecordManager) {
        if (!(localItem instanceof PlaylistStreamEntry)) {
            return;
        }
        final PlaylistStreamEntry item = (PlaylistStreamEntry) localItem;

        if (DependentPreferenceHelper.getPositionsInListsEnabled(itemProgressView.getContext())
                && item.getProgressMillis() > 0 && item.getStreamEntity().getDuration() > 0) {
            itemProgressView.setMax((int) item.getStreamEntity().getDuration());
            if (itemProgressView.getVisibility() == View.VISIBLE) {
                itemProgressView.setProgressAnimated((int) TimeUnit.MILLISECONDS
                        .toSeconds(item.getProgressMillis()));
            } else {
                itemProgressView.setProgress((int) TimeUnit.MILLISECONDS
                        .toSeconds(item.getProgressMillis()));
                ViewUtils.animate(itemProgressView, true, 500);
            }
            updateDurationMarginForProgress();
        } else if (itemProgressView.getVisibility() == View.VISIBLE) {
            ViewUtils.animate(itemProgressView, false, 500);
            updateDurationMarginForProgress(false);
        }
    }

    private View.OnTouchListener getOnTouchListener(final PlaylistStreamEntry item) {
        return (view, motionEvent) -> {
            view.performClick();
            if (itemBuilder != null && itemBuilder.getOnItemSelectedListener() != null
                    && motionEvent.getActionMasked() == MotionEvent.ACTION_DOWN) {
                itemBuilder.getOnItemSelectedListener().drag(item,
                        LocalPlaylistStreamItemHolder.this);
            }
            return false;
        };
    }
}

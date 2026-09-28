package org.schabi.newpipe.local.holder;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.local.LocalItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;

import java.time.format.DateTimeFormatter;

public abstract class PlaylistItemHolder extends LocalItemHolder {
    public final ImageView itemThumbnailView;
    final TextView itemStreamCountView;
    public final TextView itemTitleView;
    public final TextView itemUploaderView;

    public PlaylistItemHolder(final LocalItemBuilder infoItemBuilder, final int layoutId,
                              final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);

        itemThumbnailView = itemView.findViewById(R.id.itemThumbnailView);
        itemTitleView = itemView.findViewById(R.id.itemTitleView);
        itemStreamCountView = itemView.findViewById(R.id.itemStreamCountView);
        itemUploaderView = itemView.findViewById(R.id.itemUploaderView);
    }

    public PlaylistItemHolder(final LocalItemBuilder infoItemBuilder, final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_playlist_mini_item, parent);
    }

    @Override
    public void updateFromItem(final LocalItem localItem,
                               final HistoryRecordManager historyRecordManager,
                               final DateTimeFormatter dateTimeFormatter) {
        bindClickTarget(itemView, localItem);
        bindClickTarget(itemThumbnailView, localItem);
        bindClickTarget(itemTitleView, localItem);
        final View thumbnailContainer = itemView.findViewById(R.id.itemThumbnailContainer);
        if (thumbnailContainer != null) {
            bindClickTarget(thumbnailContainer, localItem);
        }

        bindLongPressTarget(itemView, localItem);
        if (thumbnailContainer != null) {
            bindLongPressTarget(thumbnailContainer, localItem);
        }
        final View titleView = itemView.findViewById(R.id.itemTitleView);
        if (titleView != null) {
            bindLongPressTarget(titleView, localItem);
        }
    }

    private void bindClickTarget(final View target, final LocalItem localItem) {
        target.setOnClickListener(view -> {
            if (itemBuilder.getOnItemSelectedListener() != null) {
                itemBuilder.getOnItemSelectedListener().selected(localItem);
            }
        });
    }

    private void bindLongPressTarget(final View target, final LocalItem localItem) {
        target.setLongClickable(true);
        target.setOnLongClickListener(view -> {
            if (itemBuilder.getOnItemSelectedListener() != null) {
                itemBuilder.getOnItemSelectedListener().held(localItem);
            }
            return true;
        });
    }
}

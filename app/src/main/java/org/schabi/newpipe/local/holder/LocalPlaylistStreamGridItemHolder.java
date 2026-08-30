package org.schabi.newpipe.local.holder;

import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.local.LocalItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.GridTitleDisplayPolicy;

import java.time.format.DateTimeFormatter;

public class LocalPlaylistStreamGridItemHolder extends LocalPlaylistStreamItemHolder {
    public LocalPlaylistStreamGridItemHolder(final LocalItemBuilder infoItemBuilder,
                                             final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_stream_playlist_grid_item, parent);
    }

    @Override
    public void updateFromItem(final LocalItem localItem,
                               final HistoryRecordManager historyRecordManager,
                               final DateTimeFormatter dateTimeFormatter) {
        GridTitleDisplayPolicy.apply(itemVideoTitleView);
        super.updateFromItem(localItem, historyRecordManager, dateTimeFormatter);
    }
}

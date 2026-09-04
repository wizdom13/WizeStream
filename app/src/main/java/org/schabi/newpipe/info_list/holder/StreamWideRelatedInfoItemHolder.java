/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.schabi.newpipe.info_list.holder;

import android.view.ViewGroup;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.GridTitleDisplayPolicy;

/** Stream holder for the wide related-video column shown beside the player. */
public final class StreamWideRelatedInfoItemHolder extends StreamInfoItemHolder {
    public StreamWideRelatedInfoItemHolder(final InfoItemBuilder infoItemBuilder,
                                           final ViewGroup parent) {
        super(infoItemBuilder, R.layout.list_stream_related_wide_item, parent);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        GridTitleDisplayPolicy.apply(itemVideoTitleView);
        super.updateFromItem(infoItem, historyRecordManager);
    }
}

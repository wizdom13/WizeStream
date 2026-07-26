package us.shandian.giga.get;

import androidx.annotation.NonNull;

import java.util.UUID;

import org.schabi.newpipe.streams.io.StoredFileHelper;

public class FinishedMission extends Mission {

    public String syncId;
    public String displayName;
    public String mimeType;

    public FinishedMission() {
    }

    public FinishedMission(@NonNull DownloadMission mission) {
        syncId = UUID.randomUUID().toString();
        source = mission.source;
        length = mission.length;
        timestamp = mission.timestamp;
        kind = mission.kind;
        storage = mission.storage;
        displayName = storage.getName();
        if (displayName == null || displayName.isEmpty()) {
            displayName = "download";
        }
        mimeType = storage.getType();
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = StoredFileHelper.DEFAULT_MIME;
        }
    }

}

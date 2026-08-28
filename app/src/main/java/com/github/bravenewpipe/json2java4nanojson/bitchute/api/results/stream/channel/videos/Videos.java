package com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.stream.channel.videos;

import com.grack.nanojson.JsonObject;
import java.io.Serializable;
public class Videos implements Serializable {


    private final String datePublished;
    private final String description;
    private final String duration;
    private final String rumbleId;
    private final String sensitivityId;
    private final String stateId;
    private final String thumbnailUrl;
    private final String videoId;
    private final String videoName;
    private final String videoUrl;
    private final int viewCount;

    public Videos(JsonObject jsonObject) {
        this.datePublished = jsonObject.getString("date_published");
        this.description = jsonObject.getString("description");
        this.duration = jsonObject.getString("duration");
        this.rumbleId = jsonObject.getString("rumble_id");
        this.sensitivityId = jsonObject.getString("sensitivity_id");
        this.stateId = jsonObject.getString("state_id");
        this.thumbnailUrl = jsonObject.getString("thumbnail_url");
        this.videoId = jsonObject.getString("video_id");
        this.videoName = jsonObject.getString("video_name");
        this.videoUrl = jsonObject.getString("video_url");
        this.viewCount = jsonObject.getInt("view_count");
    }

    public String getDatePublished() {
        return datePublished;
    }

    public String getDescription() {
        return description;
    }

    public String getDuration() {
        return duration;
    }

    public String getRumbleId() {
        return rumbleId;
    }

    public String getSensitivityId() {
        return sensitivityId;
    }

    public String getStateId() {
        return stateId;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public String getVideoId() {
        return videoId;
    }

    public String getVideoName() {
        return videoName;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public int getViewCount() {
        return viewCount;
    }
}

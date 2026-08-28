package com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.search.videos;

import com.grack.nanojson.JsonObject;
import java.io.Serializable;
public class Videos implements Serializable {


    private final Channel channel;
    private final String datePublished;
    private final String description;
    private final String duration;
    private final String sensitivityId;
    private final String thumbnailUrl;
    private final String videoId;
    private final String videoName;
    private final String videoUrl;
    private final int viewCount;

    public Videos(JsonObject jsonObject) {
        this.channel = new Channel(jsonObject.getObject("channel"));
        this.datePublished = jsonObject.getString("date_published");
        this.description = jsonObject.getString("description");
        this.duration = jsonObject.getString("duration");
        this.sensitivityId = jsonObject.getString("sensitivity_id");
        this.thumbnailUrl = jsonObject.getString("thumbnail_url");
        this.videoId = jsonObject.getString("video_id");
        this.videoName = jsonObject.getString("video_name");
        this.videoUrl = jsonObject.getString("video_url");
        this.viewCount = jsonObject.getInt("view_count");
    }

    public Channel getChannel() {
        return channel;
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

    public String getSensitivityId() {
        return sensitivityId;
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

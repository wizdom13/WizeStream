package com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.search.channels;

import com.grack.nanojson.JsonObject;
import java.io.Serializable;
public class Channels implements Serializable {


    private final String channelId;
    private final String channelName;
    private final String channelUrl;
    private final String dateCreated;
    private final String description;
    private final String lastVideoPublished;
    private final Profile profile;
    private final String sensitivityId;
    private final int subscriberCount;
    private final String thumbnailUrl;

    public Channels(JsonObject jsonObject) {
        this.channelId = jsonObject.getString("channel_id");
        this.channelName = jsonObject.getString("channel_name");
        this.channelUrl = jsonObject.getString("channel_url");
        this.dateCreated = jsonObject.getString("date_created");
        this.description = jsonObject.getString("description");
        this.lastVideoPublished = jsonObject.getString("last_video_published");
        this.profile = new Profile(jsonObject.getObject("profile"));
        this.sensitivityId = jsonObject.getString("sensitivity_id");
        this.subscriberCount = jsonObject.getInt("subscriber_count");
        this.thumbnailUrl = jsonObject.getString("thumbnail_url");
    }

    public String getChannelId() {
        return channelId;
    }

    public String getChannelName() {
        return channelName;
    }

    public String getChannelUrl() {
        return channelUrl;
    }

    public String getDateCreated() {
        return dateCreated;
    }

    public String getDescription() {
        return description;
    }

    public String getLastVideoPublished() {
        return lastVideoPublished;
    }

    public Profile getProfile() {
        return profile;
    }

    public String getSensitivityId() {
        return sensitivityId;
    }

    public int getSubscriberCount() {
        return subscriberCount;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }
}

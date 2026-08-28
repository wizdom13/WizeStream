package com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.stream.videos;

import com.grack.nanojson.JsonObject;
import java.io.Serializable;
public class Channel implements Serializable {


    private final String channelId;
    private final String channelName;
    private final String channelUrl;
    private final String thumbnailUrl;

    public Channel(JsonObject jsonObject) {
        this.channelId = jsonObject.getString("channel_id");
        this.channelName = jsonObject.getString("channel_name");
        this.channelUrl = jsonObject.getString("channel_url");
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

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }
}

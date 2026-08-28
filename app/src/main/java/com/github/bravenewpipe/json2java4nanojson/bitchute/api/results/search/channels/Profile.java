package com.github.bravenewpipe.json2java4nanojson.bitchute.api.results.search.channels;

import com.grack.nanojson.JsonObject;
import java.io.Serializable;
public class Profile implements Serializable {


    private final String profileId;
    private final String profileName;
    private final String profileThumbnailUrl;
    private final String profileUrl;

    public Profile(JsonObject jsonObject) {
        this.profileId = jsonObject.getString("profile_id");
        this.profileName = jsonObject.getString("profile_name");
        this.profileThumbnailUrl = jsonObject.getString("profile_thumbnail_url");
        this.profileUrl = jsonObject.getString("profile_url");
    }

    public String getProfileId() {
        return profileId;
    }

    public String getProfileName() {
        return profileName;
    }

    public String getProfileThumbnailUrl() {
        return profileThumbnailUrl;
    }

    public String getProfileUrl() {
        return profileUrl;
    }
}

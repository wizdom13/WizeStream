package org.schabi.newpipe.extractor.services.youtube.invidious;

import com.grack.nanojson.JsonArray;
import com.grack.nanojson.JsonObject;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.MultiInfoItemsCollector;
import org.schabi.newpipe.extractor.channel.ChannelInfoItemExtractor;
import org.schabi.newpipe.extractor.localization.DateWrapper;
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemExtractor;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;
import org.schabi.newpipe.extractor.stream.StreamType;

import java.util.List;

final class InvidiousItems {
    private InvidiousItems() { }

    static StreamInfoItemExtractor video(final JsonObject json, final InvidiousApi api) {
        return new StreamInfoItemExtractor() {
            @Override
            public String getName() {
                return json.getString("title", "");
            }
            @Override
            public String getUrl() {
                return "https://www.youtube.com/watch?v=" + json.getString("videoId", "");
            }
            @Override
            public List<Image> getThumbnails() {
                return api.images(json.getArray("videoThumbnails"));
            }
            @Override
            public StreamType getStreamType() {
                return json.getBoolean("liveNow") ? StreamType.LIVE_STREAM
                        : StreamType.VIDEO_STREAM;
            }
            @Override
            public long getDuration() {
                return json.getLong("lengthSeconds", -1);
            }
            @Override
            public long getViewCount() {
                return json.getLong("viewCount", -1);
            }
            @Override
            public String getUploaderName() {
                return json.getString("author", "");
            }
            @Override
            public String getUploaderUrl() {
                return InvidiousApi.channelUrl(json);
            }
            @Override
            public String getUploaderAvatarUrl() {
                return api.firstImage(json.getArray("authorThumbnails"));
            }
            @Override
            public boolean isUploaderVerified() {
                return json.getBoolean("authorVerified");
            }
            @Override
            public String getTextualUploadDate() {
                return json.getString("publishedText", "");
            }
            @Override
            public DateWrapper getUploadDate() {
                return InvidiousApi.date(json);
            }
            @Override
            public String getShortDescription() {
                return json.getString("description", "");
            }
            @Override
            public boolean isShortFormContent() {
                return "shortVideo".equals(json.getString("type"));
            }
        };
    }

    static StreamInfoItemsCollector videos(final JsonArray array, final int serviceId,
                                           final InvidiousApi api) {
        final StreamInfoItemsCollector collector = new StreamInfoItemsCollector(serviceId);
        if (array != null) {
            for (final Object value : array) {
                if (value instanceof JsonObject && ((JsonObject) value).has("videoId")) {
                    collector.commit(video((JsonObject) value, api));
                }
            }
        }
        return collector;
    }

    static MultiInfoItemsCollector mixed(final JsonArray array, final int serviceId,
                                         final InvidiousApi api) {
        final MultiInfoItemsCollector collector = new MultiInfoItemsCollector(serviceId);
        if (array == null) {
            return collector;
        }
        for (final Object value : array) {
            if (!(value instanceof JsonObject)) {
                continue;
            }
            final JsonObject json = (JsonObject) value;
            if (json.has("videoId")) {
                collector.commit(video(json, api));
            } else if (json.has("playlistId")) {
                collector.commit(new PlaylistInfoItemExtractor() {
                    @Override
                    public String getName() {
                        return json.getString("title", "");
                    }
                    @Override
                    public String getUrl() {
                        return "https://www.youtube.com/playlist?list=" + json
                                .getString("playlistId");
                    }
                    @Override
                    public String getThumbnailUrl() {
                        return api.image(json.getString("playlistThumbnail", ""));
                    }
                    @Override
                    public String getUploaderName() {
                        return json.getString("author", "");
                    }
                    @Override
                    public long getStreamCount() {
                        return json.getLong("videoCount", -1);
                    }
                });
            } else if (json.has("authorId")) {
                collector.commit(new ChannelInfoItemExtractor() {
                    @Override
                    public String getName() {
                        return json.getString("author", "");
                    }
                    @Override
                    public String getUrl() {
                        return InvidiousApi.channelUrl(json);
                    }
                    @Override
                    public List<Image> getThumbnails() {
                        return api.images(json.getArray("authorThumbnails"));
                    }
                    @Override
                    public String getDescription() {
                        return json.getString("description", "");
                    }
                    @Override
                    public long getSubscriberCount() {
                        return json.getLong("subCount", -1);
                    }
                    @Override
                    public long getStreamCount() {
                        return json.getLong("videoCount", -1);
                    }
                    @Override
                    public boolean isVerified() {
                        return json.getBoolean("authorVerified");
                    }
                });
            }
        }
        return collector;
    }
}

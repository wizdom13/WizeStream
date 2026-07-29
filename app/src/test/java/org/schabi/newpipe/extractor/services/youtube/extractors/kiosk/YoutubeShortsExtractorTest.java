package org.schabi.newpipe.extractor.services.youtube.extractors.kiosk;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonParser;

import org.junit.jupiter.api.Test;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.downloader.Downloader;
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler;
import org.schabi.newpipe.extractor.services.youtube.YoutubeService;
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeShortsLinkHandlerFactory;
import org.schabi.newpipe.extractor.stream.StreamInfoItemsCollector;

import java.util.Collections;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class YoutubeShortsExtractorTest {
    @Test
    void collectsNestedShortsShelfItemsAndRemovesDuplicates() throws Exception {
        final JsonObject response = JsonParser.object().from("""
                {
                  "contents": [{
                    "reelShelfRenderer": {
                      "items": [{
                        "shortsLockupViewModel": {
                          "overlayMetadata": {
                            "primaryText": {"content": "A short"},
                            "secondaryText": {"content": "123 views"}
                          },
                          "onTap": {
                            "innertubeCommand": {
                              "commandMetadata": {
                                "webCommandMetadata": {
                                  "url": "/shorts/abcdefghijk"
                                }
                              }
                            }
                          },
                          "thumbnail": {
                            "sources": [{
                              "url": "https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg"
                            }]
                          }
                        }
                      }, {
                        "shortsLockupViewModel": {
                          "overlayMetadata": {
                            "primaryText": {"content": "A duplicate"},
                            "secondaryText": {"content": "123 views"}
                          },
                          "onTap": {
                            "innertubeCommand": {
                              "commandMetadata": {
                                "webCommandMetadata": {
                                  "url": "/shorts/abcdefghijk"
                                }
                              }
                            }
                          },
                          "thumbnail": {
                            "sources": [{
                              "url": "https://i.ytimg.com/vi/abcdefghijk/hqdefault.jpg"
                            }]
                          }
                        }
                      }]
                    }
                  }]
                }
                """);
        NewPipe.init(mock(Downloader.class));
        final YoutubeService service = new YoutubeService(0);
        final YoutubeShortsExtractor extractor = new YoutubeShortsExtractor(
                service,
                new ListLinkHandler(
                        "https://www.youtube.com/shorts",
                        "https://www.youtube.com/shorts",
                        YoutubeShortsLinkHandlerFactory.KIOSK_ID,
                        Collections.emptyList(),
                        null),
                YoutubeShortsLinkHandlerFactory.KIOSK_ID);
        final StreamInfoItemsCollector collector =
                new StreamInfoItemsCollector(service.getServiceId());

        extractor.collectShorts(response, collector, new HashSet<>());

        assertEquals(1, collector.getItems().size());
        assertEquals("https://youtube.com/shorts/abcdefghijk",
                collector.getItems().get(0).getUrl());
        assertTrue(collector.getItems().get(0).isShortFormContent());
    }
}

package org.schabi.newpipe.util

import android.content.Context
import android.widget.Toast
import com.grack.nanojson.JsonParser
import com.grack.nanojson.JsonWriter
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeMusicAlbumLink

object MusicAlbumNavigation {
    @JvmStatic
    fun isAvailable(serviceId: Int, albumUrl: String?): Boolean = serviceId == ServiceList.YouTube.serviceId &&
        albumUrl?.matches(Regex("https://music\\.youtube\\.com/browse/MPRE[a-zA-Z0-9_-]+")) == true

    @JvmStatic
    fun open(context: Context, albumUrl: String?) {
        if (!isAvailable(ServiceList.YouTube.serviceId, albumUrl)) return
        Single.fromCallable {
            val body = JsonWriter.string().`object`()
                .`object`("context").`object`("client")
                .value("clientName", "WEB_REMIX")
                .value("clientVersion", YoutubeParsingHelper.getYoutubeMusicClientVersion())
                .value("hl", "en-GB").end().end()
                .value("browseId", albumUrl!!.substringAfterLast('/')).end().done()
            val headers = YoutubeParsingHelper.getYoutubeMusicHeaders()
            headers["Content-Type"] = listOf("application/json")
            val response = NewPipe.getDownloader().post(
                "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false",
                headers,
                body.toByteArray(Charsets.UTF_8)
            )
            val json = JsonParser.`object`().from(YoutubeParsingHelper.getValidJsonResponseBody(response))
            YoutubeMusicAlbumLink.playlistFromAlbum(json) ?: error("Album playlist unavailable")
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribe(
            { playlist -> context.startActivity(NavigationHelper.getIntentByLink(context, playlist)) },
            { Toast.makeText(context, R.string.music_album_unavailable, Toast.LENGTH_LONG).show() }
        )
    }
}

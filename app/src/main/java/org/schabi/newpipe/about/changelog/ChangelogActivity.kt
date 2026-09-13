package org.schabi.newpipe.about.changelog

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.schabi.newpipe.R
import org.schabi.newpipe.about.getLicenseStylesheet
import org.schabi.newpipe.databinding.ActivityChangelogBinding
import org.schabi.newpipe.util.DeviceUtils
import org.schabi.newpipe.util.EdgeToEdgeHelper
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.util.external_communication.ShareUtils

class ChangelogActivity : AppCompatActivity() {
    private lateinit var binding: ActivityChangelogBinding
    private var load: Disposable? = null
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.setTheme(this)
        super.onCreate(savedInstanceState)
        EdgeToEdgeHelper.enable(this)
        binding = ActivityChangelogBinding.inflate(layoutInflater)
        setContentView(binding.root)
        EdgeToEdgeHelper.applySystemBarPadding(binding.root)
        setSupportActionBar(binding.changelogToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.changelog_title)
        val scroll = savedInstanceState?.getInt(SCROLL) ?: 0
        val context = applicationContext
        load = Single.fromCallable { context.assets.open("changelog.html").bufferedReader().use { it.readText() } }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({ showContent(it, scroll) }, {
                Toast.makeText(this, R.string.changelog_load_failed, Toast.LENGTH_LONG).show()
                finish()
            })
    }

    private fun showContent(html: String, scroll: Int) {
        val viewer = if (DeviceUtils.supportsWebView()) runCatching { WebView(this) }.getOrNull() else null
        if (viewer == null) {
            val text = changelogTextView(this, html, false)
            binding.changelogContent.addView(text)
            text.post { text.scrollTo(0, scroll) }
            return
        }
        webView = viewer
        viewer.setBackgroundColor(Color.TRANSPARENT)
        viewer.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            blockNetworkLoads = true
            textZoom = (100 * resources.configuration.fontScale).toInt()
        }
        var restoreScroll = scroll
        viewer.webViewClient = object : WebViewClient() {
            @Deprecated("Used on Android 6")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = openLink(url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = openLink(request.url.toString())

            override fun onPageFinished(view: WebView, url: String) {
                val position = restoreScroll
                restoreScroll = 0
                if (position > 0) view.post { view.scrollTo(0, position) }
            }
        }
        binding.changelogContent.addView(viewer)
        val themed = html.replace("</head>", "<style>${getLicenseStylesheet(this)}</style></head>")
        viewer.loadDataWithBaseURL(BASE_URL, themed, "text/html", "UTF-8", null)
    }

    private fun openLink(url: String): Boolean {
        if (url.startsWith("#") || url.startsWith("$BASE_URL#")) return false
        if (Uri.parse(url).scheme in listOf("https", "http")) ShareUtils.openUrlInBrowser(this, url)
        return true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(SCROLL, webView?.scrollY ?: binding.changelogContent.getChildAt(0)?.scrollY ?: 0)
        super.onSaveInstanceState(outState)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        load?.dispose()
        webView?.let {
            binding.changelogContent.removeView(it)
            it.stopLoading()
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        private const val BASE_URL = "https://appassets.androidplatform.net/assets/changelog.html"
        private const val SCROLL = "changelog-scroll"
    }
}

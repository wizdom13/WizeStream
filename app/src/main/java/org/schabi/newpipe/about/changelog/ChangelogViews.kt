package org.schabi.newpipe.about.changelog

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.ViewGroup
import androidx.core.text.HtmlCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.R as MaterialR
import org.jsoup.Jsoup
import org.schabi.newpipe.util.ThemeHelper
import org.schabi.newpipe.views.NewPipeTextView

internal fun changelogTextView(context: Context, html: String, dialog: Boolean): NestedScrollView {
    val density = context.resources.displayMetrics.density
    val document = Jsoup.parse(html)
    document.select("details, nav").remove()
    val text = NewPipeTextView(context).apply {
        setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyMedium)
        setTextColor(ThemeHelper.resolveColorFromAttr(context, MaterialR.attr.colorOnSurface))
        setLinkTextColor(ThemeHelper.resolveColorFromAttr(context, MaterialR.attr.colorPrimary))
        setPadding((24 * density).toInt(), (8 * density).toInt(), (24 * density).toInt(), (16 * density).toInt())
        setText(HtmlCompat.fromHtml(document.body().html(), HtmlCompat.FROM_HTML_MODE_LEGACY))
        movementMethod = LinkMovementMethod.getInstance()
    }
    return NestedScrollView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (dialog) (context.resources.displayMetrics.heightPixels * 0.5f).toInt() else ViewGroup.LayoutParams.MATCH_PARENT
        )
        addView(text)
    }
}

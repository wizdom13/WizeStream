package org.schabi.newpipe.fragments.detail

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import org.schabi.newpipe.extractor.MetaInfo
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeContentDisclosure
import org.schabi.newpipe.util.external_communication.ShareUtils

/** A compact, selectable provider disclosure after the description and before technical metadata. */
object ContentDisclosureView {
    @JvmStatic
    fun addTo(parent: LinearLayout, metadata: List<MetaInfo>) {
        for (disclosure in metadata.filterIsInstance<YoutubeContentDisclosure>()) {
            val label = AppCompatTextView(parent.context)
            label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall)
            label.text = SpannableStringBuilder(disclosure.title).apply {
                setSpan(StyleSpan(Typeface.BOLD), 0, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (disclosure.content.content.isNotBlank()) append("\n").append(disclosure.content.content)
            }
            label.setTextIsSelectable(true)
            label.setPadding(0, 0, 0, (12 * parent.resources.displayMetrics.density).toInt())
            label.setOnClickListener { ShareUtils.openUrlInBrowser(parent.context, YoutubeContentDisclosure.HELP_URL) }
            parent.addView(label)
        }
    }
}

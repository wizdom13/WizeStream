package org.schabi.newpipe.local.history

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import org.schabi.newpipe.R
import org.schabi.newpipe.databinding.ToolbarLayoutBinding

/** Keeps the privacy indicator visible without taking horizontal space from search. */
object IncognitoToolbarUi {
    @JvmStatic
    fun bindContentInset(binding: ToolbarLayoutBinding, content: View) {
        binding.root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val toolbarHeight = binding.toolbar.height
            if (toolbarHeight <= 0) return@addOnLayoutChangeListener
            val statusHeight = if (binding.incognitoStatus.isVisible) binding.incognitoStatus.height else 0
            val params = content.layoutParams as? ViewGroup.MarginLayoutParams
                ?: return@addOnLayoutChangeListener
            val topMargin = toolbarHeight + statusHeight
            if (params.topMargin != topMargin) {
                params.topMargin = topMargin
                content.layoutParams = params
            }
        }
    }

    @JvmStatic
    fun prepareMenu(context: Context, menu: Menu, status: View) {
        val enabled = IncognitoMode.isEnabled(context)
        val item = menu.findItem(R.id.action_incognito)
            ?: menu.add(Menu.NONE, R.id.action_incognito, Menu.NONE, R.string.incognito_start)
        item.setTitle(if (enabled) R.string.incognito_active else R.string.incognito_start)
        item.isCheckable = true
        item.isChecked = enabled
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        status.isVisible = enabled
    }
}

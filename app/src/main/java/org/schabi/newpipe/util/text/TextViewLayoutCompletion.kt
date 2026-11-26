/*
 * SPDX-FileCopyrightText: 2026 WizeStream contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package org.schabi.newpipe.util.text

import android.view.View
import android.widget.TextView
import androidx.annotation.MainThread
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable

/** Waits for current text measurements; clearing the binding cancels a pending completion. */
object TextViewLayoutCompletion {
    @JvmStatic
    @MainThread
    fun afterLayout(view: TextView, disposables: CompositeDisposable, completion: Runnable) {
        if (disposables.isDisposed) return
        val ready = view.isLaidOut && !view.isLayoutRequested && hasTextLayout(view)
        if (ready && !view.isInLayout) {
            completion.run()
            return
        }

        lateinit var pending: Disposable
        val complete = Runnable {
            if (disposables.remove(pending)) completion.run()
        }
        val listener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(v: View, l: Int, t: Int, r: Int, b: Int, oldL: Int, oldT: Int, oldR: Int, oldB: Int) {
                if (hasTextLayout(view)) {
                    view.removeOnLayoutChangeListener(this)
                    // View.layout clears its layout-request flag after notifying listeners.
                    // Let it return before the completion changes text or its maximum lines,
                    // so the resulting request can shrink both the text and its parent row.
                    view.post(complete)
                }
            }
        }
        pending = Disposable.fromAction {
            view.removeOnLayoutChangeListener(listener)
            view.removeCallbacks(complete)
        }
        if (disposables.add(pending)) {
            // A valid child layout can also be observed while its parent is still laying out.
            if (ready) view.post(complete) else view.addOnLayoutChangeListener(listener)
        }
    }

    private fun hasTextLayout(view: TextView): Boolean = view.width > 0 && view.layout != null
}

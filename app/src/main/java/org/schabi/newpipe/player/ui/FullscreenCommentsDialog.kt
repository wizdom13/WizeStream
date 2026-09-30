package org.schabi.newpipe.player.ui

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.fragments.list.comments.CommentRepliesFragment
import org.schabi.newpipe.fragments.list.comments.CommentsFragment
import org.schabi.newpipe.util.DeviceUtils

/** A separate focus window keeps remote navigation and Back inside the comments panel. */
class FullscreenCommentsDialog : DialogFragment() {
    private var videoContent: View? = null
    private var originalEndMargin = 0
    private var observedRoot: View? = null
    private var backStackListener: FragmentManager.OnBackStackChangedListener? = null
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateViewport() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View = inflater.inflate(R.layout.fullscreen_comments_sidebar, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<View>(R.id.fullscreenCommentsClose).setOnClickListener { dismiss() }
        val back = view.findViewById<View>(R.id.fullscreenCommentsBack)
        back.setOnClickListener { childFragmentManager.popBackStack() }
        backStackListener = FragmentManager.OnBackStackChangedListener {
            back.visibility = if (childFragmentManager.backStackEntryCount > 0) View.VISIBLE else View.GONE
        }.also {
            childFragmentManager.addOnBackStackChangedListener(it)
        }
        back.visibility = if (childFragmentManager.backStackEntryCount > 0) View.VISIBLE else View.GONE
        if (childFragmentManager.findFragmentById(R.id.fullscreenCommentsContent) == null) {
            val args = requireArguments()
            childFragmentManager.beginTransaction().replace(
                R.id.fullscreenCommentsContent,
                CommentsFragment.getInstance(args.getInt(SERVICE), args.getString(URL).orEmpty(), args.getString(TITLE).orEmpty())
            ).commitNow()
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { panel, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            panel.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.setCanceledOnTouchOutside(false)
        dialog?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setGravity(Gravity.END)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        observedRoot = requireActivity().window.decorView.also {
            it.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }
        updateViewport()
        view?.findViewById<View>(R.id.fullscreenCommentsClose)?.requestFocus()
    }

    internal fun showReplies(comment: CommentsInfoItem) {
        childFragmentManager.beginTransaction()
            .replace(R.id.fullscreenCommentsContent, CommentRepliesFragment(comment), CommentRepliesFragment.TAG)
            .addToBackStack(CommentRepliesFragment.TAG)
            .commit()
    }

    private fun updateViewport() {
        if (dialog?.isShowing != true) return
        val window = dialog?.window ?: return
        val content = activity?.findViewById<View>(R.id.playerVideoContent) ?: return
        val available = (content.parent as? View)?.width ?: return
        if (available <= 0) return
        val desired = (TvPlayerFocusPolicy.commentsWidthDp(DeviceUtils.isTv(requireContext())) * resources.displayMetrics.density).toInt()
        val width = desired.coerceAtMost((available * 0.45f).toInt()).coerceAtLeast(1)
        if (window.attributes.width != width || window.attributes.height != ViewGroup.LayoutParams.MATCH_PARENT) {
            window.setLayout(width, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        if (videoContent !== content) {
            restoreViewport()
            videoContent = content
            originalEndMargin = (content.layoutParams as ViewGroup.MarginLayoutParams).marginEnd
        }
        val params = content.layoutParams as ViewGroup.MarginLayoutParams
        if (params.marginEnd != originalEndMargin + width) {
            params.marginEnd = originalEndMargin + width
            content.layoutParams = params
        }
    }

    private fun restoreViewport() {
        videoContent?.let { content ->
            val params = content.layoutParams as ViewGroup.MarginLayoutParams
            params.marginEnd = originalEndMargin
            content.layoutParams = params
        }
        videoContent = null
    }

    override fun onDismiss(dialog: DialogInterface) {
        restoreViewport()
        super.onDismiss(dialog)
    }

    override fun onStop() {
        observedRoot?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnGlobalLayoutListener(layoutListener)
        observedRoot = null
        restoreViewport()
        super.onStop()
    }

    override fun onDestroyView() {
        backStackListener?.let(childFragmentManager::removeOnBackStackChangedListener)
        backStackListener = null
        super.onDestroyView()
    }

    companion object {
        private const val SERVICE = "service"
        private const val URL = "url"
        private const val TITLE = "title"

        fun newInstance(serviceId: Int, url: String, title: String) = FullscreenCommentsDialog().apply {
            arguments = Bundle().apply {
                putInt(SERVICE, serviceId)
                putString(URL, url)
                putString(TITLE, title)
            }
        }
    }
}

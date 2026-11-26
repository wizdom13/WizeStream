package org.schabi.newpipe.settings

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import org.schabi.newpipe.BuildConfig
import org.schabi.newpipe.R
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.settings.InvidiousInstanceDirectory.ResponseFailure
import org.schabi.newpipe.util.external_communication.ShareUtils

/** Keeps directory failures visible and diagnosable without exposing response bodies or settings. */
object InvidiousDirectoryFailure {
    internal data class Description(val message: String, val report: String)

    @JvmStatic
    fun show(fragment: Fragment, error: Throwable, retry: Runnable): AlertDialog? {
        val context = fragment.context ?: return null
        val owner = fragment.viewLifecycleOwnerLiveData.value ?: return null
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return null
        val description = describe(context, error)
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.invidious_directory_error_title)
            .setMessage(description.message)
            .setPositiveButton(R.string.retry) { _, _ ->
                if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) retry.run()
            }
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.invidious_directory_copy_details, null)
            .create()
        val observer = object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) = dialog.dismiss()
        }
        owner.lifecycle.addObserver(observer)
        dialog.setOnDismissListener { owner.lifecycle.removeObserver(observer) }
        dialog.show()
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
            ShareUtils.copyToClipboard(context, description.report)
        }
        return dialog
    }

    internal fun describe(context: Context, error: Throwable): Description {
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        val response = causes.filterIsInstance<ResponseFailure>().firstOrNull()
        val (category, reason) = when {
            response is ResponseFailure.Http -> "HTTP" to context.getString(R.string.invidious_directory_error_http, response.status)

            response is ResponseFailure.Invalid -> response.reason.name to context.getString(
                when (response.reason) {
                    ResponseFailure.Reason.HTML -> R.string.invidious_directory_error_html
                    ResponseFailure.Reason.TOO_LARGE -> R.string.invidious_directory_error_size
                    ResponseFailure.Reason.INVALID_JSON -> R.string.invidious_directory_error_response
                }
            )

            causes.any { it is ReCaptchaException } -> "ACCESS_LIMITED" to context.getString(R.string.invidious_directory_error_limited)

            causes.any { it is SSLException } -> "TLS" to context.getString(R.string.error_ssl_exception)

            causes.any { it is UnknownHostException } -> "DNS" to context.getString(R.string.error_unknown_host)

            causes.any { it is SocketTimeoutException } -> "TIMEOUT" to context.getString(R.string.error_timeout)

            causes.any { it is ConnectException || it is IOException } -> "CONNECTION" to context.getString(R.string.error_connect_host)

            else -> "OTHER" to context.getString(R.string.invidious_directory_load_failed)
        }
        val report = buildString {
            appendLine("WizeStream Invidious directory")
            appendLine("URL: ${InvidiousInstanceDirectory.DIRECTORY_URL}")
            appendLine("App: ${BuildConfig.VERSION_NAME}")
            appendLine("Android SDK: ${Build.VERSION.SDK_INT}")
            appendLine("Failure: $category")
            if (response is ResponseFailure.Http) appendLine("HTTP: ${response.status}")
            append("Exception types: ${causes.joinToString(" -> ") { it.javaClass.name }}")
        }
        return Description("$reason\n\n${context.getString(R.string.invidious_directory_error_retry)}", report)
    }
}

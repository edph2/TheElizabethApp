package uk.elizabeth.aac.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.Settings
import android.webkit.WebView
import android.webkit.WebViewClient

/** Prints the paper communication board (or saves it as a PDF) through Android's print system. */
object PaperBoardPrinter {
    // Held until printing starts, otherwise the WebView can be garbage collected mid-job.
    private var webView: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun print(context: Context, html: String) {
        val view = WebView(context)
        view.settings.javaScriptEnabled = false
        view.settings.blockNetworkLoads = true
        view.settings.allowFileAccess = false
        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(v: WebView, url: String?) {
                val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                manager.print(
                    "Communication board",
                    v.createPrintDocumentAdapter("Communication board"),
                    PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4.asLandscape()).build(),
                )
                webView = null
            }
        }
        webView = view
        view.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
    }
}

/** Turns the app's home-screen mode on or off (see the activity-alias in the manifest). */
object HomeScreenMode {
    private fun component(context: Context) = ComponentName(context, "uk.elizabeth.aac.HomeScreen")

    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun setEnabled(context: Context, enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        // Let the carer choose (or stop choosing) this app as the home screen.
        context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

package com.androidautobrowser.browser.web

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.view.View
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * Unrestricted browser WebView: all http/https links load in-app.
 * Non-web schemes are ignored so Android Auto is not interrupted.
 */
object BrowserWebViewFactory {

    private const val CHROME_DESKTOP_USER_AGENT: String =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

    private val WEB_SCHEMES = setOf("http", "https", "about", "data", "javascript", "blob")

    data class Callbacks(
        val onProgress: (Int) -> Unit = {},
        val onTitle: (String?) -> Unit = {},
        val onUrl: (String?) -> Unit = {},
        val onPageStarted: (String?) -> Unit = {},
        val onPageFinished: (String?) -> Unit = {},
        val onReceivedError: () -> Unit = {},
        val onEnterFullscreen: (View, WebChromeClient.CustomViewCallback) -> Unit = { _, _ -> },
        val onExitFullscreen: () -> Unit = {},
    )

    @SuppressLint("SetJavaScriptEnabled")
    fun create(
        context: Context,
        callbacks: Callbacks = Callbacks(),
    ): WebView = WebView(context).also { configure(it, callbacks) }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(
        view: WebView,
        callbacks: Callbacks = Callbacks(),
    ) {
        view.setBackgroundColor(Color.BLACK)
        view.isFocusable = true
        view.isFocusableInTouchMode = true
        view.isHorizontalScrollBarEnabled = false
        view.isVerticalScrollBarEnabled = true

        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest,
            ): Boolean = shouldBlockScheme(request.url)

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean =
                shouldBlockScheme(Uri.parse(url))

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                callbacks.onPageStarted(url)
                callbacks.onUrl(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                callbacks.onPageFinished(url)
                callbacks.onUrl(url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    callbacks.onReceivedError()
                }
            }
        }

        view.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                callbacks.onProgress(newProgress)
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                callbacks.onTitle(title)
            }

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view != null && callback != null) {
                    callbacks.onEnterFullscreen(view, callback)
                }
            }

            override fun onHideCustomView() {
                callbacks.onExitFullscreen()
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }
        }

        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            setSupportZoom(true)
            mediaPlaybackRequiresUserGesture = false
            javaScriptCanOpenWindowsAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowContentAccess = true
            allowFileAccess = false
            userAgentString = CHROME_DESKTOP_USER_AGENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(view, true)
        }
    }

    private fun shouldBlockScheme(uri: Uri?): Boolean {
        if (uri == null) return false
        val scheme = uri.scheme?.lowercase() ?: return false
        return scheme !in WEB_SCHEMES
    }
}

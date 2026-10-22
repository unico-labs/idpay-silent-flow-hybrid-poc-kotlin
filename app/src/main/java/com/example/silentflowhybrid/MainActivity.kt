package com.example.silentflowhybrid

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/**
 * IDPay Silent Flow Hybrid POC: the device data collection happens on a web
 * page (collect-page/) — the Unico web SDK runs silently (no camera) and
 * returns via deep link. By default the page is preloaded in a hidden WebView
 * so the SDK is ready before the user taps; Custom Tabs is the alternative.
 * The app then creates an IDPay transaction that is approved silently when
 * the collected device matches the user history.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val DEEP_LINK_SCHEME = "silentflowhybrid"
        private const val STATUS_CHALLENGE_FINISHED = "challenge-finished"
        private const val PREPARE_RETRY_MS = 3_000L
    }

    private lateinit var mainText: TextView
    private lateinit var externalUserIdInput: TextInputEditText
    private lateinit var cpfInput: TextInputEditText
    private lateinit var binInput: TextInputEditText
    private lateinit var lastDigitsInput: TextInputEditText
    private lateinit var bearerTokenInput: TextInputEditText
    private lateinit var logTextView: TextView
    private lateinit var logScrollView: ScrollView
    private lateinit var overlay: View
    private lateinit var overlaySpinner: ProgressBar
    private lateinit var overlayIcon: TextView
    private lateinit var overlayText: TextView
    private lateinit var root: FrameLayout
    private var collectWebView: WebView? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    private var runTransactionAfterCollect = false

    private val collectPageUrl: Uri =
        Uri.parse(PocConfig.COLLECT_PAGE_URL).buildUpon().path("/").build()
    private var collectPageReady = false
    private var collecting = false
    private var pendingExternalUserId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mainText = findViewById(R.id.mainText)
        externalUserIdInput = findViewById(R.id.externalUserIdInput)
        cpfInput = findViewById(R.id.cpfInput)
        binInput = findViewById(R.id.binInput)
        lastDigitsInput = findViewById(R.id.lastDigitsInput)
        bearerTokenInput = findViewById(R.id.bearerTokenInput)
        logTextView = findViewById(R.id.logTextView)
        logScrollView = findViewById(R.id.logScrollView)
        overlay = findViewById(R.id.overlay)
        overlaySpinner = findViewById(R.id.overlaySpinner)
        overlayIcon = findViewById(R.id.overlayIcon)
        overlayText = findViewById(R.id.overlayText)
        root = findViewById(R.id.root)

        findViewById<TextView>(R.id.clearLogButton).setOnClickListener {
            logTextView.text = getString(R.string.logs_placeholder)
        }

        if (PocConfig.USE_WEBVIEW) preloadWebView()

        handleDeepLink(intent)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        discardWebView()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    // ---------------------------------------------------------- button hooks

    fun collectDeviceData(view: View) {
        runTransactionAfterCollect = false
        startCollect()
    }

    fun createSilentTransaction(view: View) {
        createTransaction()
    }

    fun runFullFlow(view: View) {
        runTransactionAfterCollect = true
        startCollect()
    }

    // ------------------------------------------------------------ collection

    // The identifier typed on screen; the CPF is the fallback when it is empty.
    // In a real integration this is whatever id the client has for the user.
    private fun resolveExternalUserId(): String =
        externalUserIdInput.text.toString().trim()
            .ifEmpty { cpfInput.text.toString().trim() }

    private fun startCollect() {
        val externalUserId = resolveExternalUserId()
        if (externalUserId.isEmpty()) {
            addLog("ERROR: fill the externalUserId (or the CPF)")
            runTransactionAfterCollect = false
            return
        }

        if (PocConfig.USE_WEBVIEW) {
            collectInWebView(externalUserId)
        } else {
            collectInCustomTab(externalUserId)
        }
    }

    /**
     * The SDK is already prepared on the hidden page, so only the send is
     * left. A tap during the prepare waits for it to finish.
     */
    private fun collectInWebView(externalUserId: String) {
        collecting = true
        mainText.text = getString(R.string.status_collect_silent)
        showOverlayProcessing(getString(R.string.overlay_collecting))

        if (collectPageReady) {
            sendCollect(externalUserId)
        } else {
            pendingExternalUserId = externalUserId
            if (collectWebView == null) preloadWebView()
        }
    }

    private fun sendCollect(externalUserId: String) {
        pendingExternalUserId = null
        // JSONObject.quote escapes the identifier as a JS string literal.
        collectWebView?.evaluateJavascript("window.collect(${JSONObject.quote(externalUserId)})", null)
    }

    private fun collectInCustomTab(externalUserId: String) {
        // The identifier travels in the URL fragment: it never leaves the
        // browser (fragments are not sent to servers) and the page strips it
        // before the SDK loads.
        val url = Uri.parse(PocConfig.COLLECT_PAGE_URL)
            .buildUpon()
            .path("/")
            .encodedFragment("externalUserId=" + Uri.encode(externalUserId))
            .build()
        mainText.text = getString(R.string.status_collect_browser)
        addLog("collect: opening $url")
        openInCustomTab(url)
    }

    /**
     * Loads the collect page in a hidden WebView ahead of the tap: the SDK
     * prepares while the user is still on the screen. One SDK session serves
     * one validation, so a fresh page is loaded after each collection.
     */
    private fun preloadWebView() {
        mainHandler.removeCallbacks(retryPreload)
        collectPageReady = false
        val webView = collectWebView ?: createWebView().also { collectWebView = it }
        webView.loadUrl(collectPageUrl.toString())
    }

    private val retryPreload = Runnable { preloadWebView() }

    // INVISIBLE (not GONE) keeps the full-screen size the SDK reads for the
    // device fingerprint.
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        val webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.visibility = View.INVISIBLE
        webView.addJavascriptInterface(CollectBridge(), "CollectBridge")
        webView.webViewClient = object : WebViewClient() {
            // The bridge is exposed to whatever page is loaded: keep the
            // WebView on the collect page origin.
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                val target = request?.url ?: return true
                return target.scheme != collectPageUrl.scheme ||
                    target.authority != collectPageUrl.authority
            }

            // The page renderer runs in a separate process that the system
            // may kill (e.g. low memory). Without this, the app crashes too.
            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?,
            ): Boolean {
                addLog("collect: WebView renderer gone, reloading")
                discardWebView()
                if (collecting) onCollectFailed()
                preloadWebView()
                return true
            }
        }
        root.addView(
            webView,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return webView
    }

    private fun discardWebView() {
        val webView = collectWebView ?: return
        collectWebView = null
        collectPageReady = false
        root.removeView(webView)
        webView.destroy()
    }

    /** Events from the collect page, called on a WebView background thread. */
    private inner class CollectBridge {
        @JavascriptInterface
        fun onReady() = runOnUiThread {
            collectPageReady = true
            pendingExternalUserId?.let { sendCollect(it) }
        }

        @JavascriptInterface
        fun onPrepareFailed(detail: String) = runOnUiThread {
            addLog("collect: SDK prepare failed ($detail), retrying")
            if (collecting) onCollectFailed()
            mainHandler.postDelayed(retryPreload, PREPARE_RETRY_MS)
        }

        @JavascriptInterface
        fun onResult(status: String, detail: String) = runOnUiThread {
            if (detail.isNotEmpty()) addLog("collect: $detail")
            onCollectResult(status)
            preloadWebView()
        }
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != DEEP_LINK_SCHEME) return

        val status = data.getQueryParameter("status")
        if (status == STATUS_CHALLENGE_FINISHED) {
            addLog("challenge finished — back in the app")
        } else {
            onCollectResult(status)
        }
    }

    private fun onCollectResult(status: String?) {
        addLog("collect result: status=$status")
        if (status == "ok") onCollectReturned() else onCollectFailed()
    }

    private fun onCollectFailed() {
        collecting = false
        pendingExternalUserId = null
        runTransactionAfterCollect = false
        hideOverlay()
        mainText.text = getString(R.string.status_collect_failed)
    }

    /**
     * The collection has been sent, so the transaction can be created right
     * away.
     */
    private fun onCollectReturned() {
        collecting = false
        hideOverlay()
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        mainText.text = getString(R.string.status_collect_ready, ts)

        if (runTransactionAfterCollect) {
            runTransactionAfterCollect = false
            createTransaction()
        }
    }

    // ----------------------------------------------------------- transaction

    private fun createTransaction() {
        val externalUserId = resolveExternalUserId()
        val cpf = cpfInput.text.toString().trim()
        val bin = binInput.text.toString().trim()
        val lastDigits = lastDigitsInput.text.toString().trim()
        val token = bearerTokenInput.text.toString().trim()

        val missing = mutableListOf<String>()
        if (cpf.isEmpty()) missing.add("CPF")
        if (bin.isEmpty()) missing.add("binDigits")
        if (lastDigits.isEmpty()) missing.add("lastDigits")
        if (token.isEmpty()) missing.add("Bearer token")
        if (missing.isNotEmpty()) {
            addLog("ERROR: missing ${missing.joinToString(", ")}")
            return
        }
        if (PocConfig.COMPANY_ID.startsWith("YOUR_")) {
            addLog("ERROR: fill COMPANY_ID in PocConfig.kt")
            return
        }

        showOverlayProcessing(getString(R.string.overlay_processing))

        val body = JSONObject()
            .put("identity", JSONObject().put("key", "cpf").put("value", cpf))
            .put("orderNumber", "silent-flow-hybrid-poc-${System.currentTimeMillis()}")
            .put("company", PocConfig.COMPANY_ID)
            .put("redirectUrl", "$DEEP_LINK_SCHEME://done?status=$STATUS_CHALLENGE_FINISHED")
            .put(
                "card",
                JSONObject()
                    .put("binDigits", bin)
                    .put("lastDigits", lastDigits)
                    .put("expirationDate", "12/28")
                    .put("name", "Silent Flow Hybrid Poc"),
            )
            .put("value", 10.50)
            .put("additionalInfo", JSONObject().put("externalUserID", externalUserId))

        addLog("transaction: POST ${PocConfig.IDPAY_BASE_URL}/api/public/v1/credit/transaction")

        // In a real integration this request is made by the CLIENT's backend,
        // which then calls the IDPay API server-to-server. The POC skips that
        // hop and calls IDPay directly with a token pasted on the screen.
        Thread {
            try {
                val url = URL("${PocConfig.IDPAY_BASE_URL}/api/public/v1/credit/transaction")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.connectTimeout = 30_000
                conn.readTimeout = 30_000
                conn.doOutput = true
                conn.outputStream.use {
                    it.write(body.toString().toByteArray(StandardCharsets.UTF_8))
                }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val responseBody = stream?.bufferedReader()?.use { it.readText() } ?: ""
                runOnUiThread { onTransactionResponse(code, responseBody) }
            } catch (e: Exception) {
                runOnUiThread {
                    addLog("request ERROR: ${e.message}")
                    hideOverlay()
                }
            }
        }.start()
    }

    private fun onTransactionResponse(code: Int, body: String) {
        val json = try { JSONObject(body) } catch (e: Exception) { null }
        if (code !in 200..299 || json == null) {
            addLog("HTTP $code: ${body.take(400)}")
            hideOverlay()
            return
        }

        val status = json.optString("status")
        val id = json.optString("id")
        val link = json.optString("link")
        addLog("HTTP $code · id=$id · status=$status")

        when {
            status == "approved" -> {
                addLog("✔ SILENT APPROVAL — no challenge needed")
                showOverlayApproved()
            }
            link.isNotEmpty() -> {
                addLog("challenge required — opening fallback")
                hideOverlay()
                openInCustomTab(Uri.parse(link))
            }
            else -> {
                addLog("status=$status without link — not approved")
                hideOverlay()
            }
        }
    }

    // --------------------------------------------------------------- overlay

    private val hideOverlayRunnable = Runnable { hideOverlay() }

    private fun showOverlayProcessing(message: String) {
        mainHandler.removeCallbacks(hideOverlayRunnable)
        overlay.setBackgroundColor(ContextCompat.getColor(this, R.color.unico_dark_navy))
        overlaySpinner.visibility = View.VISIBLE
        overlayIcon.visibility = View.GONE
        overlayText.text = message
        overlay.setOnClickListener(null)
        overlay.visibility = View.VISIBLE
    }

    private fun showOverlayApproved() {
        overlay.setBackgroundColor(ContextCompat.getColor(this, R.color.result_ok))
        overlaySpinner.visibility = View.GONE
        overlayIcon.text = "✔"
        overlayIcon.visibility = View.VISIBLE
        overlayText.text = getString(R.string.overlay_approved)
        overlay.setOnClickListener { hideOverlay() }
        overlay.visibility = View.VISIBLE
        mainHandler.postDelayed(hideOverlayRunnable, 3_000)
    }

    private fun hideOverlay() {
        overlay.visibility = View.GONE
    }

    // --------------------------------------------------------------- helpers

    private fun openInCustomTab(url: Uri) {
        CustomTabsIntent.Builder()
            .setUrlBarHidingEnabled(true)
            .build()
            .launchUrl(this, url)
    }

    private fun addLog(message: String) {
        val ts = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val current = logTextView.text.toString()
        val base = if (current == getString(R.string.logs_placeholder)) "" else "$current\n"
        logTextView.text = "$base[$ts] $message"
        logScrollView.post { logScrollView.fullScroll(View.FOCUS_DOWN) }
    }
}

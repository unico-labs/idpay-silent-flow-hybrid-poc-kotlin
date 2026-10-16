package com.example.silentflowhybrid

/**
 * POC-only configuration. In a real integration none of this lives in the app:
 * the company id belongs to the client's backend and the collect page is a
 * public HTTPS page owned by the client (or by Unico).
 */
object PocConfig {

    const val IDPAY_BASE_URL = "https://transactions.transactional.uat.unico.app"

    const val COMPANY_ID = "YOUR_COMPANY_ID"

    // Where the collect page (collect-page/) is served. For local testing keep
    // localhost and run `adb reverse tcp:3000 tcp:3000` — the page must be on
    // a host that is both registered in the SDK Key and a browser secure
    // context (over plain HTTP, only localhost qualifies).
    const val COLLECT_PAGE_URL = "http://localhost:3000"

    // Container used to open the collect page: in-app WebView (true) or
    // Custom Tabs (false). The page and the flow are identical in both.
    const val USE_WEBVIEW = false
}

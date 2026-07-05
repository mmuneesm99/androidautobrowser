package com.androidautobrowser.browser

/**
 * Quick-launch destinations. The main home card embeds YouTube;
 * Chrome opens full-screen browsing.
 */
enum class BrowserDestination(
    val label: String,
    val url: String,
) {
    YOUTUBE(
        label = "YouTube",
        url = "https://www.youtube.com",
    ),
    CHROME(
        label = "Chrome",
        url = "https://www.google.com",
    );

    companion object {
        val DEFAULT: BrowserDestination = YOUTUBE
    }
}

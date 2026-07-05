package com.androidautobrowser.browser.info

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Shared HTTP helpers with redirects, gzip, and clear error messages.
 */
internal object NetworkJson {

    fun get(url: String, timeoutMs: Int = 12_000): String {
        var current = url
        var redirects = 0
        while (redirects < 5) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                requestMethod = "GET"
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/121.0.0.0 Mobile Safari/537.36",
                )
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Accept-Encoding", "gzip")
                setRequestProperty("Accept-Language", "ml-IN,ml;q=0.9,en-IN;q=0.8,en;q=0.7")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: error("Redirect without Location ($code)")
                    current = if (location.startsWith("http")) {
                        location
                    } else {
                        URL(URL(current), location).toString()
                    }
                    redirects++
                    continue
                }
                val stream = if (code in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: error("HTTP $code for $current")
                }
                val encoding = connection.contentEncoding?.lowercase()
                val input = if (encoding == "gzip") GZIPInputStream(stream) else stream
                return BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }
        error("Too many redirects for $url")
    }

    fun postForm(url: String, body: String, timeoutMs: Int = 20_000): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/121.0.0.0 Mobile Safari/537.36",
            )
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: error("HTTP $code")
            }
            stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
}

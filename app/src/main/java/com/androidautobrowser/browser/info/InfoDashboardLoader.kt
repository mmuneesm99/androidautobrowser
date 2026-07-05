package com.androidautobrowser.browser.info

import android.location.Location
import android.util.Log
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.concurrent.Executors

data class WeatherInfo(
    val temperatureC: Int,
    val feelsLikeC: Int,
    val humidityPercent: Int,
    val windKmh: Int,
    val condition: String,
    val weatherCode: Int,
) {
    val isSevere: Boolean
        get() = weatherCode in SEVERE_WEATHER_CODES

    val severeAlertText: String?
        get() = if (isSevere) condition else null

    companion object {
        private val SEVERE_WEATHER_CODES = setOf(
            65, 67, 75, 82, 86, 95, 96, 99,
        )
    }
}

data class IpLocation(
    val latitude: Double,
    val longitude: Double,
    val label: String,
)

/**
 * Loads weather, Malayalam headlines, and weather alerts.
 * Uses a thread pool so one slow feed does not block the others.
 */
object InfoDashboardLoader {

    private const val TAG = "InfoDashboardLoader"
    private val executor = Executors.newFixedThreadPool(4)

    private val malayalamNewsFeeds = listOf(
        "https://news.google.com/rss?hl=ml&gl=IN&ceid=IN:ml",
        "https://news.google.com/rss/headlines/section/topic/NATION.ml_in?hl=ml&gl=IN&ceid=IN:ml",
        "https://news.google.com/rss/search?q=%E0%B4%95%E0%B5%87%E0%B4%B0%E0%B4%B3&hl=ml&gl=IN&ceid=IN:ml",
    )

    private val weatherNewsFeeds = listOf(
        "https://news.google.com/rss/search?q=%E0%B4%AE%E0%B4%B4+OR+%E0%B4%95%E0%B4%BE%E0%B4%B2%E0%B4%BE%E0%B4%B5%E0%B4%B8%E0%B5%8D%E0%B4%A5+OR+IMD+OR+rain&hl=ml&gl=IN&ceid=IN:ml",
        "https://news.google.com/rss/search?q=Kerala+rain+OR+weather+alert+OR+IMD&hl=en-IN&gl=IN&ceid=IN:en",
    )

    private val weatherKeywords = listOf(
        "മഴ", "കാലാവസ്ഥ", "അലേർട്ട്", "അലര്‍ട്ട്", "മുന്നറിയിപ്പ്",
        "ചുഴലിക്കാറ്റ്", "വെള്ളപ്പൊക്കം", "ഇടിമിന്നൽ", "കാറ്റ്",
        "rain", "weather", "alert", "cyclone", "storm", "flood", "imd",
        "thunder", "heatwave", "heat wave", "orange alert", "red alert",
        "yellow alert",
    )

    fun loadIpLocation(onResult: (Result<IpLocation>) -> Unit) {
        executor.execute {
            val result = runCatching {
                // Free HTTPS IP lookup — used when GPS is slow/unavailable.
                val json = JSONObject(NetworkJson.get("https://ipapi.co/json/"))
                val lat = json.getDouble("latitude")
                val lon = json.getDouble("longitude")
                val city = json.optString("city").ifBlank { "Nearby" }
                val region = json.optString("region").ifBlank { json.optString("country_name") }
                IpLocation(
                    latitude = lat,
                    longitude = lon,
                    label = listOf(city, region).filter { it.isNotBlank() }.joinToString(", "),
                )
            }.recoverCatching {
                val json = JSONObject(NetworkJson.get("https://ipinfo.io/json"))
                val parts = json.getString("loc").split(",")
                val city = json.optString("city").ifBlank { "Nearby" }
                val region = json.optString("region")
                IpLocation(
                    latitude = parts[0].toDouble(),
                    longitude = parts[1].toDouble(),
                    label = listOf(city, region).filter { it.isNotBlank() }.joinToString(", "),
                )
            }
            if (result.isFailure) {
                Log.w(TAG, "IP location failed", result.exceptionOrNull())
            }
            onResult(result)
        }
    }

    fun loadWeather(location: Location, onResult: (Result<WeatherInfo>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val url =
                    "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=${location.latitude}" +
                        "&longitude=${location.longitude}" +
                        "&current=temperature_2m,relative_humidity_2m," +
                        "apparent_temperature,weather_code,wind_speed_10m" +
                        "&wind_speed_unit=kmh"
                val json = JSONObject(NetworkJson.get(url))
                val current = json.getJSONObject("current")
                val code = current.getInt("weather_code")
                WeatherInfo(
                    temperatureC = current.getDouble("temperature_2m").toInt(),
                    feelsLikeC = current.getDouble("apparent_temperature").toInt(),
                    humidityPercent = current.getDouble("relative_humidity_2m").toInt(),
                    windKmh = current.getDouble("wind_speed_10m").toInt(),
                    condition = weatherCodeLabel(code),
                    weatherCode = code,
                )
            }
            if (result.isFailure) {
                Log.w(TAG, "Weather failed", result.exceptionOrNull())
            }
            onResult(result)
        }
    }

    fun loadHeadlines(onResult: (Result<List<String>>) -> Unit) {
        executor.execute {
            val result = runCatching {
                var titles = emptyList<String>()
                for (feed in malayalamNewsFeeds) {
                    titles = runCatching { parseRssTitles(NetworkJson.get(feed)).take(3) }
                        .onFailure { Log.w(TAG, "Headline feed failed: $feed", it) }
                        .getOrDefault(emptyList())
                    if (titles.isNotEmpty()) break
                }
                check(titles.isNotEmpty()) { "No Malayalam headlines" }
                titles
            }
            onResult(result)
        }
    }

    fun loadWeatherAlerts(onResult: (Result<List<String>>) -> Unit) {
        executor.execute {
            val result = runCatching {
                val alerts = linkedSetOf<String>()
                for (feed in weatherNewsFeeds + malayalamNewsFeeds) {
                    val titles = runCatching { parseRssTitles(NetworkJson.get(feed)) }
                        .getOrDefault(emptyList())
                    titles.filter { isWeatherRelated(it) }.forEach { alerts += it }
                    if (alerts.size >= 2) break
                }
                alerts.take(2)
            }
            onResult(result)
        }
    }

    private fun isWeatherRelated(title: String): Boolean {
        val lower = title.lowercase()
        return weatherKeywords.any { keyword -> lower.contains(keyword.lowercase()) }
    }

    private fun parseRssTitles(xml: String): List<String> {
        // Prefer regex — more tolerant of CDATA / odd Google News markup.
        val regexTitles = parseTitlesByRegex(xml)
        if (regexTitles.isNotEmpty()) return regexTitles

        val titles = mutableListOf<String>()
        val parser = XmlPullParserFactory.newInstance().newPullParser().apply {
            setInput(xml.reader())
        }
        var inItem = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT && titles.size < 5) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name?.substringAfter(':').orEmpty()
                    when (name) {
                        "item", "entry" -> inItem = true
                        "title" -> if (inItem) {
                            val title = cleanHeadline(parser.nextText().trim())
                            if (title.isNotEmpty()) titles += title
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name?.substringAfter(':').orEmpty()
                    if (name == "item" || name == "entry") inItem = false
                }
            }
            event = parser.next()
        }
        return titles
    }

    private fun parseTitlesByRegex(xml: String): List<String> {
        val itemBlocks = Regex(
            "<item\\b[\\s\\S]*?</item>|<entry\\b[\\s\\S]*?</entry>",
            RegexOption.IGNORE_CASE,
        ).findAll(xml)
        val titleRegex = Regex(
            "<title[^>]*>\\s*(?:<!\\[CDATA\\[(.*?)]]>|([^<]*))\\s*</title>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        return itemBlocks.mapNotNull { block ->
            val match = titleRegex.find(block.value) ?: return@mapNotNull null
            val raw = (match.groupValues.getOrNull(1)?.ifBlank { null }
                ?: match.groupValues.getOrNull(2).orEmpty()).trim()
            cleanHeadline(raw).ifBlank { null }
        }.filter { it.isNotEmpty() }.take(5).toList()
    }

    private fun cleanHeadline(title: String): String {
        val decoded = title
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
        val cut = decoded.lastIndexOf(" - ")
        return if (cut > 12) decoded.substring(0, cut).trim() else decoded
    }

    private fun weatherCodeLabel(code: Int): String = when (code) {
        0 -> "Clear sky"
        1, 2 -> "Partly cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm with hail"
        else -> "Mixed conditions"
    }
}

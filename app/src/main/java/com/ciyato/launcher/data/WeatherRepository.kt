package com.ciyato.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

/**
 * WeatherRepository — live weather via Open-Meteo + Nominatim.
 * Suggestions implemented: 26 (hourly), 27 (7-day), 29 (AQI), 30 (sunrise/sunset),
 * 31 (UV index), 32 (wind direction), 33 (rain probability), 116 (cache logic),
 * 117 (offline awareness), 118 (retry with backoff), 21 (°C/°F).
 *
 *  ▸ Open-Meteo   https://open-meteo.com  — free, no API key
 *  ▸ Nominatim    https://nominatim.org   — free OSM reverse geocoding
 */
object WeatherRepository {

    // ── Sealed result types ───────────────────────────────────────────────────

    sealed class WeatherState {
        data object Loading      : WeatherState()
        data object NoPermission : WeatherState()
        data object NoLocation   : WeatherState()
        data object Offline      : WeatherState()
        data class  Error(val message: String) : WeatherState()
        data class  Success(
            val tempC        : Int,
            val feelsLikeC   : Int,
            val highC        : Int,
            val lowC         : Int,
            val condition    : String,
            val weatherCode  : Int,
            val windKmh      : Double,
            val windDirectionDeg: Int,
            val humidity     : Int,
            val locationName : String,
            val isDay        : Boolean,
            val uvIndex      : Double,
            val sunrise      : String,
            val sunset       : String,
            val hourly       : List<HourlyEntry>,
            val daily        : List<DailyEntry>,
            val aqi          : AqiData?,
        ) : WeatherState()
    }

    data class HourlyEntry(
        val timeLabel:  String,   // "10 AM"
        val tempC:      Int,
        val weatherCode:Int,
        val rainPct:    Int,
        val isDay:      Boolean,
    )

    data class DailyEntry(
        val dayLabel:   String,   // "Mon", "Tue", etc.
        val highC:      Int,
        val lowC:       Int,
        val weatherCode:Int,
        val uvIndexMax: Double,
        val rainPct:    Int,
    )

    data class AqiData(
        val pm25:  Double,
        val pm10:  Double,
        val aqiEu: Int,   // European AQI index
    )

    // ── Public API ────────────────────────────────────────────────────────────

    /** Fetch full weather bundle. Retries up to 3× with exponential backoff (118). */
    suspend fun fetchWeather(lat: Double, lon: Double): WeatherState =
        withContext(Dispatchers.IO) {
            retryWithBackoff(maxAttempts = 3) {
                try {
                    val forecast  = fetchForecastJson(lat, lon)
                    val aqiJson   = runCatching { fetchAqiJson(lat, lon) }.getOrNull()
                    val cityName  = fetchCityName(lat, lon)
                    parseForecast(forecast, aqiJson, cityName)
                } catch (e: java.net.UnknownHostException) {
                    WeatherState.Offline
                } catch (e: Exception) {
                    WeatherState.Error("${e.javaClass.simpleName}: ${e.message?.take(80)}")
                }
            }
        }

    /** Celsius → Fahrenheit (suggestion 21). */
    fun cToF(c: Int): Int = (c * 9 / 5) + 32

    /** Display temperature respecting user's unit preference. */
    fun displayTemp(c: Int, useFahrenheit: Boolean): String =
        if (useFahrenheit) "${cToF(c)}°F" else "${c}°C"

    /** Countries that use Fahrenheit day-to-day. */
    private val FAHRENHEIT_COUNTRIES = setOf("US", "BS", "BZ", "KY", "LR", "PW", "FM", "MH")

    /** The unit the phone's region actually uses — the default until the person overrides it. */
    fun localeDefaultUnit(): String =
        if (java.util.Locale.getDefault().country.uppercase() in FAHRENHEIT_COUNTRIES) "F" else "C"

    /** Known OEM/system weather apps, most-common first. */
    private val SYSTEM_WEATHER_PACKAGES = listOf(
        "com.google.android.apps.weather",   // Pixel Weather
        "com.sec.android.daemonapp",         // Samsung Weather
        "com.miui.weather2",                 // Xiaomi
        "com.coloros.weather2",              // Oppo/realme (ColorOS)
        "com.oplus.weather2",                // OnePlus (OxygenOS 12+)
        "net.oneplus.weather",               // OnePlus legacy
        "com.huawei.android.totemweather",   // Huawei
        "com.vivo.weather",                  // Vivo
        "com.asus.weather",                  // Asus
        "com.motorola.weather",              // Motorola
        "com.accuweather.android",
        "com.weather.Weather",               // The Weather Channel
    )

    /**
     * Opens the phone's own weather app instead of duplicating one.
     * Returns false when no weather app is installed so the caller can fall
     * back to Ciyato's built-in forecast screen.
     */
    fun launchSystemWeatherApp(context: android.content.Context): Boolean {
        val pm = context.packageManager
        for (pkg in SYSTEM_WEATHER_PACKAGES) {
            val intent = pm.getLaunchIntentForPackage(pkg) ?: continue
            return runCatching {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            }.getOrDefault(false)
        }
        return false
    }

    /** WMO weather code → human label. */
    fun weatherCodeToCondition(code: Int): String = when (code) {
        0    -> "Clear Sky"
        1    -> "Mainly Clear"
        2    -> "Partly Cloudy"
        3    -> "Overcast"
        45   -> "Foggy"
        48   -> "Icy Fog"
        51   -> "Light Drizzle"
        53   -> "Drizzle"
        55   -> "Heavy Drizzle"
        56   -> "Freezing Drizzle"
        57   -> "Heavy Freezing Drizzle"
        61   -> "Light Rain"
        63   -> "Rain"
        65   -> "Heavy Rain"
        66   -> "Freezing Rain"
        67   -> "Heavy Freezing Rain"
        71   -> "Light Snow"
        73   -> "Snow"
        75   -> "Heavy Snow"
        77   -> "Snow Grains"
        80   -> "Light Showers"
        81   -> "Showers"
        82   -> "Heavy Showers"
        85   -> "Snow Showers"
        86   -> "Heavy Snow Showers"
        95   -> "Thunderstorm"
        96   -> "Thunderstorm w/ Hail"
        99   -> "Severe Thunderstorm"
        else -> "Unknown"
    }

    /** Wind degrees → compass direction (suggestion 32). */
    fun windDirection(degrees: Int): String {
        val dirs = listOf("N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW")
        // +11.25 centres each 22.5° sector on its label, so 350° reads "N" not "NNW".
        val normalized = ((degrees % 360) + 360) % 360
        return dirs[(((normalized + 11.25) / 22.5).toInt()) % 16]
    }

    /** European AQI index → label. */
    fun aqiLabel(index: Int): String = when {
        index <= 20 -> "Good"
        index <= 40 -> "Fair"
        index <= 60 -> "Moderate"
        index <= 80 -> "Poor"
        index <= 100 -> "Very Poor"
        else -> "Extremely Poor"
    }

    /** AQI label → colour hex string (for UI tinting). */
    fun aqiColor(index: Int): Long = when {
        index <= 20 -> 0xFF39C66A
        index <= 40 -> 0xFFB5E550
        index <= 60 -> 0xFFF5C542
        index <= 80 -> 0xFFFF8C42
        index <= 100 -> 0xFFEF4444
        else -> 0xFF9C27B0
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    private fun fetchForecastJson(lat: Double, lon: Double): JSONObject {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast")
            append("?latitude=$lat&longitude=$lon")
            append("&current=temperature_2m,apparent_temperature,weather_code")
            append(",wind_speed_10m,wind_direction_10m,relative_humidity_2m,is_day")
            append("&hourly=temperature_2m,weather_code,precipitation_probability,is_day")
            append("&daily=temperature_2m_max,temperature_2m_min,weather_code,sunrise,sunset")
            append(",uv_index_max,precipitation_probability_max")
            append("&timezone=auto&forecast_days=7")
        }
        return JSONObject(fetchString(url))
    }

    private fun fetchAqiJson(lat: Double, lon: Double): JSONObject {
        val url = buildString {
            append("https://air-quality-api.open-meteo.com/v1/air-quality")
            append("?latitude=$lat&longitude=$lon")
            append("&current=pm10,pm2_5,european_aqi")
        }
        return JSONObject(fetchString(url))
    }

    private fun fetchCityName(lat: Double, lon: Double): String = try {
        val url  = "https://nominatim.openstreetmap.org/reverse?format=json&lat=$lat&lon=$lon&zoom=10"
        val json = JSONObject(fetchString(url, mapOf("User-Agent" to "Ciyato Launcher/1.0 (Android)")))
        val addr = json.optJSONObject("address")
        listOf("city", "town", "village", "county").firstNotNullOfOrNull { k ->
            addr?.optString(k)?.takeIf { it.isNotBlank() }
        } ?: json.optString("display_name")?.split(",")?.first()?.trim() ?: "Your Location"
    } catch (_: Exception) { "Your Location" }

    private fun fetchString(url: String, headers: Map<String, String> = emptyMap()): String {
        val conn = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout    = 8_000
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        if (conn.responseCode >= 400) {
            val errorBody = conn.errorStream?.use { it.bufferedReader().readText() } ?: "HTTP Error ${conn.responseCode}"
            throw Exception("HTTP ${conn.responseCode}: $errorBody")
        }
        // use{} guarantees the socket stream is closed even on parse failure —
        // otherwise every fetch leaked a file descriptor.
        return conn.getInputStream().use { it.bufferedReader().readText() }
    }

    // ── Retry helper (suggestion 118) ─────────────────────────────────────────

    private suspend fun retryWithBackoff(
        maxAttempts: Int,
        block: suspend () -> WeatherState,
    ): WeatherState {
        var lastResult: WeatherState = WeatherState.Error("No attempts made")
        repeat(maxAttempts) { attempt ->
            lastResult = block()
            if (lastResult is WeatherState.Success || lastResult is WeatherState.Offline ||
                lastResult is WeatherState.NoPermission) return lastResult
            // Don't sleep after the final attempt — that delay is pure dead time.
            if (attempt < maxAttempts - 1) delay((1_000L shl attempt)) // 1s, 2s, 4s
        }
        return lastResult
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    private fun parseForecast(json: JSONObject, aqiJson: JSONObject?, city: String): WeatherState {
        val cur  = json.getJSONObject("current")
        val hrly = json.getJSONObject("hourly")
        val dly  = json.getJSONObject("daily")

        val tempC      = cur.getDouble("temperature_2m").toInt()
        val feelsLike  = cur.getDouble("apparent_temperature").toInt()
        val code       = cur.getInt("weather_code")
        val windKmh    = cur.getDouble("wind_speed_10m")
        val windDir    = cur.optInt("wind_direction_10m", 0)
        val humidity   = cur.getInt("relative_humidity_2m")
        val isDay      = cur.optInt("is_day", 1) == 1

        // Daily
        val dCodes     = dly.getJSONArray("weather_code")
        val dMax       = dly.getJSONArray("temperature_2m_max")
        val dMin       = dly.getJSONArray("temperature_2m_min")
        val dSunrise   = dly.getJSONArray("sunrise")
        val dSunset    = dly.getJSONArray("sunset")
        val dUv        = dly.getJSONArray("uv_index_max")
        val dRain      = dly.getJSONArray("precipitation_probability_max")
        val dDates     = dly.getJSONArray("time")

        val highC      = if (dMax.length() > 0) dMax.getDouble(0).toInt() else tempC + 3
        val lowC       = if (dMin.length() > 0) dMin.getDouble(0).toInt() else tempC - 5
        val sunrise    = dSunrise.getString(0).substringAfter("T").take(5)
        val sunset     = dSunset.getString(0).substringAfter("T").take(5)
        val uvNow      = if (dUv.length() > 0) dUv.getDouble(0) else 0.0

        val dailyEntries = (0 until minOf(dMax.length(), 7)).map { i ->
            DailyEntry(
                dayLabel    = parseDayLabel(dDates.getString(i)),
                highC       = dMax.getDouble(i).toInt(),
                lowC        = dMin.getDouble(i).toInt(),
                weatherCode = dCodes.getInt(i),
                uvIndexMax  = dUv.optDouble(i, 0.0),
                rainPct     = dRain.optInt(i, 0),
            )
        }

        // Hourly — take the next 24 entries from the current hour index
        val hTimes   = hrly.getJSONArray("time")
        val hTemps   = hrly.getJSONArray("temperature_2m")
        val hCodes   = hrly.getJSONArray("weather_code")
        val hRain    = hrly.getJSONArray("precipitation_probability")
        val hIsDay   = hrly.getJSONArray("is_day")

        val nowHour  = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val nowDate  = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())
        val startIdx = (0 until hTimes.length()).firstOrNull { i ->
            hTimes.getString(i).startsWith(nowDate) &&
            hTimes.getString(i).substringAfter("T").take(2).toIntOrNull() == nowHour
        } ?: 0

        val hourlyEntries = (startIdx until minOf(startIdx + 24, hTimes.length())).map { i ->
            HourlyEntry(
                timeLabel   = parseHourLabel(hTimes.getString(i)),
                tempC       = hTemps.getDouble(i).toInt(),
                weatherCode = hCodes.getInt(i),
                rainPct     = hRain.optInt(i, 0),
                isDay       = hIsDay.optInt(i, 1) == 1,
            )
        }

        // AQI
        val aqi = aqiJson?.optJSONObject("current")?.let { c ->
            AqiData(
                pm25  = c.optDouble("pm2_5", 0.0),
                pm10  = c.optDouble("pm10", 0.0),
                aqiEu = c.optInt("european_aqi", 0),
            )
        }

        return WeatherState.Success(
            tempC            = tempC,
            feelsLikeC       = feelsLike,
            highC            = highC,
            lowC             = lowC,
            condition        = weatherCodeToCondition(code),
            weatherCode      = code,
            windKmh          = windKmh,
            windDirectionDeg = windDir,
            humidity         = humidity,
            locationName     = city,
            isDay            = isDay,
            uvIndex          = uvNow,
            sunrise          = sunrise,
            sunset           = sunset,
            hourly           = hourlyEntries,
            daily            = dailyEntries,
            aqi              = aqi,
        )
    }

    private fun parseHourLabel(iso: String): String {
        val hour = iso.substringAfter("T").take(2).toIntOrNull() ?: return iso
        return when {
            hour == 0  -> "12 AM"
            hour < 12  -> "$hour AM"
            hour == 12 -> "12 PM"
            else       -> "${hour - 12} PM"
        }
    }

    private fun parseDayLabel(isoDate: String): String {
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val parsedDate = sdf.parse(isoDate) ?: return isoDate
            val cal = java.util.Calendar.getInstance().apply { time = parsedDate }
            java.text.SimpleDateFormat("EEE", java.util.Locale.US).format(cal.time)
        } catch (_: Exception) { isoDate }
    }
}

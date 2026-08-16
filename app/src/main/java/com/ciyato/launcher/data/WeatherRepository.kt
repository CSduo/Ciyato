package com.ciyato.launcher.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

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
            /** True when this is a cached snapshot served because a live fetch just failed. */
            val isStale      : Boolean = false,
            /** When [isStale], the time (epoch millis) this snapshot was actually fetched. */
            val cachedAtMillis: Long? = null,
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

    /**
     * Fetch full weather bundle. Each underlying HTTP call retries transient
     * failures internally via [NetworkClient] (up to 3× with exponential
     * backoff, suggestion 118) — this function itself makes exactly one pass
     * over that already-retried result, so a non-transient failure (e.g. a
     * malformed response) doesn't get retried 3 more times for nothing.
     */
    suspend fun fetchWeather(lat: Double, lon: Double): WeatherState =
        withContext(Dispatchers.IO) {
            try {
                val forecast = fetchForecastJson(lat, lon)
                // AQI is supplementary: if it fails, the rest of the forecast
                // is still correct and useful, so its failure is intentionally
                // discarded here rather than sinking the whole weather result.
                val aqiJson = runCatching { fetchAqiJson(lat, lon) }.getOrNull()
                val cityName = fetchCityName(lat, lon)
                parseForecast(forecast, aqiJson, cityName)
            } catch (e: java.net.UnknownHostException) {
                WeatherState.Offline
            } catch (e: Exception) {
                WeatherState.Error("${e.javaClass.simpleName}: ${e.message?.take(80)}")
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

    // ── HTTP helpers — all timeout/retry logic lives in NetworkClient ──────────

    /**
     * Coarsens a coordinate to ~1.1 km before it leaves the device.
     *
     * These go out as URL query strings, which land in the access log of every
     * intermediary and of the endpoint itself. Sending a raw Double meant
     * disclosing roughly centimetre-precision home location to three separate
     * hosts — while LocationHelper's own doc told the reader "location never
     * leaves the device". Two decimals is indistinguishable for a weather
     * forecast, an air-quality reading, or a city-name lookup, and stops the
     * request from being a precise home address.
     */
    private fun coarse(value: Double): String = String.format(Locale.US, "%.2f", value)

    private suspend fun fetchForecastJson(lat: Double, lon: Double): JSONObject {
        val url = buildString {
            append("https://api.open-meteo.com/v1/forecast")
            append("?latitude=${coarse(lat)}&longitude=${coarse(lon)}")
            append("&current=temperature_2m,apparent_temperature,weather_code")
            append(",wind_speed_10m,wind_direction_10m,relative_humidity_2m,is_day")
            append("&hourly=temperature_2m,weather_code,precipitation_probability,is_day")
            append("&daily=temperature_2m_max,temperature_2m_min,weather_code,sunrise,sunset")
            append(",uv_index_max,precipitation_probability_max")
            append("&timezone=auto&forecast_days=7")
        }
        return JSONObject(NetworkClient.fetchText(url))
    }

    private suspend fun fetchAqiJson(lat: Double, lon: Double): JSONObject {
        val url = buildString {
            append("https://air-quality-api.open-meteo.com/v1/air-quality")
            append("?latitude=${coarse(lat)}&longitude=${coarse(lon)}")
            append("&current=pm10,pm2_5,european_aqi")
        }
        return JSONObject(NetworkClient.fetchText(url))
    }

    private suspend fun fetchCityName(lat: Double, lon: Double): String = try {
        val url  = "https://nominatim.openstreetmap.org/reverse?format=json&lat=${coarse(lat)}&lon=${coarse(lon)}&zoom=10"
        val json = JSONObject(NetworkClient.fetchText(url, mapOf("User-Agent" to "Ciyato Launcher/1.0 (Android)")))
        val addr = json.optJSONObject("address")
        listOf("city", "town", "village", "county").firstNotNullOfOrNull { k ->
            addr?.optString(k)?.takeIf { it.isNotBlank() }
        } ?: json.optString("display_name")?.split(",")?.first()?.trim() ?: "Your Location"
    } catch (_: Exception) {
        // Reverse geocoding is cosmetic (just the display label) — the
        // coordinates-based forecast above is unaffected by this failing, so
        // falling back to an honest generic label beats sinking the whole
        // weather result over a geocoder hiccup.
        "Your Location"
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

// ── Offline cache (suggestions 116/117) ─────────────────────────────────────
//
// Persists the fields needed to redraw the full weather UI (not just the
// temperature) so that when a live fetch fails, the caller (LauncherViewModel,
// which owns the DataStore) can show the last real snapshot instead of a bare
// "offline" screen — clearly marked stale via [WeatherRepository.WeatherState.Success.isStale].
// Top-level (not nested in the object) so callers can use it as a plain extension.

fun WeatherRepository.WeatherState.Success.toCacheJson(): String {
    val root = JSONObject()
    root.put("tempC", tempC)
    root.put("feelsLikeC", feelsLikeC)
    root.put("highC", highC)
    root.put("lowC", lowC)
    root.put("condition", condition)
    root.put("weatherCode", weatherCode)
    root.put("windKmh", windKmh)
    root.put("windDirectionDeg", windDirectionDeg)
    root.put("humidity", humidity)
    root.put("locationName", locationName)
    root.put("isDay", isDay)
    root.put("uvIndex", uvIndex)
    root.put("sunrise", sunrise)
    root.put("sunset", sunset)
    root.put("hourly", JSONArray().apply {
        hourly.forEach { h ->
            put(JSONObject().apply {
                put("timeLabel", h.timeLabel)
                put("tempC", h.tempC)
                put("weatherCode", h.weatherCode)
                put("rainPct", h.rainPct)
                put("isDay", h.isDay)
            })
        }
    })
    root.put("daily", JSONArray().apply {
        daily.forEach { d ->
            put(JSONObject().apply {
                put("dayLabel", d.dayLabel)
                put("highC", d.highC)
                put("lowC", d.lowC)
                put("weatherCode", d.weatherCode)
                put("uvIndexMax", d.uvIndexMax)
                put("rainPct", d.rainPct)
            })
        }
    })
    aqi?.let {
        root.put("aqi", JSONObject().apply {
            put("pm25", it.pm25)
            put("pm10", it.pm10)
            put("aqiEu", it.aqiEu)
        })
    }
    return root.toString()
}

/** Restores a cached snapshot for offline/error display, marked [WeatherRepository.WeatherState.Success.isStale]. */
fun weatherStateFromCacheJson(json: String, cachedAtMillis: Long): WeatherRepository.WeatherState.Success? {
    if (json.isBlank()) return null
    return try {
        val root = JSONObject(json)
        val hourlyArr = root.optJSONArray("hourly")
        val dailyArr = root.optJSONArray("daily")
        WeatherRepository.WeatherState.Success(
            tempC = root.getInt("tempC"),
            feelsLikeC = root.getInt("feelsLikeC"),
            highC = root.getInt("highC"),
            lowC = root.getInt("lowC"),
            condition = root.getString("condition"),
            weatherCode = root.getInt("weatherCode"),
            windKmh = root.getDouble("windKmh"),
            windDirectionDeg = root.getInt("windDirectionDeg"),
            humidity = root.getInt("humidity"),
            locationName = root.getString("locationName"),
            isDay = root.getBoolean("isDay"),
            uvIndex = root.getDouble("uvIndex"),
            sunrise = root.getString("sunrise"),
            sunset = root.getString("sunset"),
            hourly = (0 until (hourlyArr?.length() ?: 0)).map { i ->
                val h = hourlyArr!!.getJSONObject(i)
                WeatherRepository.HourlyEntry(
                    timeLabel = h.getString("timeLabel"),
                    tempC = h.getInt("tempC"),
                    weatherCode = h.getInt("weatherCode"),
                    rainPct = h.getInt("rainPct"),
                    isDay = h.getBoolean("isDay"),
                )
            },
            daily = (0 until (dailyArr?.length() ?: 0)).map { i ->
                val d = dailyArr!!.getJSONObject(i)
                WeatherRepository.DailyEntry(
                    dayLabel = d.getString("dayLabel"),
                    highC = d.getInt("highC"),
                    lowC = d.getInt("lowC"),
                    weatherCode = d.getInt("weatherCode"),
                    uvIndexMax = d.getDouble("uvIndexMax"),
                    rainPct = d.getInt("rainPct"),
                )
            },
            aqi = root.optJSONObject("aqi")?.let {
                WeatherRepository.AqiData(pm25 = it.getDouble("pm25"), pm10 = it.getDouble("pm10"), aqiEu = it.getInt("aqiEu"))
            },
            isStale = true,
            cachedAtMillis = cachedAtMillis,
        )
    } catch (_: Exception) {
        // Corrupt or old-format cache (e.g. from a previous app version) — there
        // is nothing useful to recover, so the caller falls back to its normal
        // honest offline/error state instead of crashing on garbage input.
        null
    }
}

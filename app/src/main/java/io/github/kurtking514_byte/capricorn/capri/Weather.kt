package io.github.kurtking514_byte.capricorn.capri

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 3.9e: current weather for the status scan, from Open-Meteo (free, no account, no key).
 * Uses Android's "approximate location" (asked once via chat "weather"). The position is rounded
 * to 0.1° (about 10 km) before it leaves the phone, and nothing else is sent. The result is
 * cached for [FRESH_MS]; it refreshes when you come home or open the scan and it's older.
 */
object Weather {
    data class Info(val temp: Int, val code: Int, val at: Long)

    private const val PREFS = "weather"
    const val FRESH_MS = 30 * 60_000L
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var busy = false

    fun allowed(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun cached(context: Context): Info? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains("at")) return null
        return Info(p.getInt("temp", 0), p.getInt("code", 0), p.getLong("at", 0L))
    }

    fun fresh(info: Info?) = info != null && System.currentTimeMillis() - info.at < FRESH_MS

    /** Fetches new weather if allowed and the cached one is stale; [onDone] runs on the main thread (null = failed / skipped). */
    fun refresh(context: Context, force: Boolean = false, onDone: (Info?) -> Unit = {}) {
        val app = context.applicationContext
        if (!allowed(app) || busy || (!force && fresh(cached(app)))) { onDone(null); return }
        busy = true
        locate(app) { loc ->
            val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lat = loc?.latitude ?: prefs.getString("lat", null)?.toDoubleOrNull()
            val lon = loc?.longitude ?: prefs.getString("lon", null)?.toDoubleOrNull()
            if (lat == null || lon == null) { busy = false; onDone(null); return@locate }
            Thread {
                val info = fetch(lat, lon)
                if (info != null) prefs.edit()
                    .putInt("temp", info.temp).putInt("code", info.code).putLong("at", info.at)
                    .putString("lat", round(lat)).putString("lon", round(lon)).apply()
                busy = false
                main.post { onDone(info) }
            }.start()
        }
    }

    private fun round(v: Double) = String.format(Locale.US, "%.1f", v)

    private fun locate(context: Context, done: (Location?) -> Unit) {
        val lm = context.getSystemService(LocationManager::class.java) ?: return done(null)
        try {
            val last = listOf(LocationManager.NETWORK_PROVIDER, "fused", LocationManager.PASSIVE_PROVIDER)
                .mapNotNull { p -> try { lm.getLastKnownLocation(p) } catch (e: Exception) { null } }
                .maxByOrNull { it.time }
            if (last != null && System.currentTimeMillis() - last.time < 3 * 60 * 60_000L) return done(last)
            if (!lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) return done(last)
            LocationManagerCompat.getCurrentLocation(
                lm, LocationManager.NETWORK_PROVIDER, null as android.os.CancellationSignal?, ContextCompat.getMainExecutor(context),
                androidx.core.util.Consumer<Location?> { loc -> done(loc ?: last) },
            )
        } catch (e: SecurityException) {
            done(null)
        }
    }

    private fun fetch(lat: Double, lon: Double): Info? = try {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${round(lat)}&longitude=${round(lon)}&current=temperature_2m,weather_code")
        val c = url.openConnection() as HttpURLConnection
        c.connectTimeout = 8_000
        c.readTimeout = 8_000
        try {
            if (c.responseCode != 200) null
            else {
                val cur = JSONObject(c.inputStream.bufferedReader().readText()).getJSONObject("current")
                Info(cur.getDouble("temperature_2m").roundToInt(), cur.getInt("weather_code"), System.currentTimeMillis())
            }
        } finally { c.disconnect() }
    } catch (e: Exception) { null }

    /** Short word for the WMO weather code Open-Meteo sends. */
    fun describe(code: Int): String = when (code) {
        0 -> "CLEAR"
        1, 2 -> "CLOUDY"
        3 -> "OVERCAST"
        45, 48 -> "FOG"
        in 51..57 -> "DRIZZLE"
        in 61..67 -> "RAIN"
        in 71..77, 85, 86 -> "SNOW"
        in 80..82 -> "SHOWERS"
        in 95..99 -> "STORM"
        else -> "—"
    }

    fun wet(code: Int) = code in 51..67 || code in 80..82 || code in 95..99

    /** "JUST NOW", "12 MIN AGO", "3 H AGO". */
    fun age(info: Info): String {
        val min = (System.currentTimeMillis() - info.at) / 60_000L
        return when { min < 1 -> "JUST NOW"; min < 60 -> "$min MIN AGO"; else -> "${min / 60} H AGO" }
    }
}

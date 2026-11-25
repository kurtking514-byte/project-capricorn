package io.github.kurtking514_byte.capricorn.capri

import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * One page the chatbox search can open (3.14). Android pages have an [action]; Capri's own
 * screens have a [chat] word instead, which goes through her chat (so she reacts to it).
 */
data class SettingPage(val title: String, val words: List<String>, val action: String? = null, val chat: String? = null) {
    val isCapri get() = chat != null
}

/** 3.14: settings pages for the chatbox live search. */
object SettingsSearch {
    private fun p(title: String, action: String, vararg words: String) = SettingPage(title, words.toList(), action = action)
    private fun c(title: String, chat: String, vararg words: String) = SettingPage(title, words.toList(), chat = chat)

    val PAGES = listOf(
        p("Wi-Fi", Settings.ACTION_WIFI_SETTINGS, "wifi", "wi-fi", "internet", "network"),
        p("Bluetooth", Settings.ACTION_BLUETOOTH_SETTINGS, "bluetooth", "bt", "pair", "headphones", "earbuds"),
        p("Hotspot", Settings.ACTION_WIRELESS_SETTINGS, "hotspot", "tethering", "share internet", "connections"),
        p("Mobile network", Settings.ACTION_NETWORK_OPERATOR_SETTINGS, "mobile data", "data", "sim", "carrier", "roaming"),
        p("Airplane mode", Settings.ACTION_AIRPLANE_MODE_SETTINGS, "airplane", "flight", "plane"),
        p("Display", Settings.ACTION_DISPLAY_SETTINGS, "display", "brightness", "screen", "dark mode", "font size", "refresh rate"),
        p("Sound", Settings.ACTION_SOUND_SETTINGS, "sound", "volume", "ringtone", "vibration"),
        p("Battery", "android.intent.action.POWER_USAGE_SUMMARY", "battery", "power", "battery saver"),
        p("Storage", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, "storage", "space", "memory"),
        p("Apps", Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS, "apps", "applications", "permissions"),
        p("Notifications", "android.settings.NOTIFICATION_SETTINGS", "notifications", "alerts"),
        p("Location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, "location", "gps"),
        p("Security", Settings.ACTION_SECURITY_SETTINGS, "security", "lock screen", "fingerprint", "password", "pin", "face unlock"),
        p("Privacy", Settings.ACTION_PRIVACY_SETTINGS, "privacy"),
        p("Language", Settings.ACTION_LOCALE_SETTINGS, "language", "locale"),
        p("Keyboard", Settings.ACTION_INPUT_METHOD_SETTINGS, "keyboard", "input", "typing"),
        p("Date & time", Settings.ACTION_DATE_SETTINGS, "date", "time", "clock", "time zone"),
        p("Accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, "accessibility"),
        p("About phone", Settings.ACTION_DEVICE_INFO_SETTINGS, "about", "phone info", "model", "android version"),
        p("Developer options", Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "developer", "usb debugging"),
        p("NFC", Settings.ACTION_NFC_SETTINGS, "nfc", "tap to pay"),
        p("Default apps", Settings.ACTION_HOME_SETTINGS, "default", "home app", "launcher"),
        p("Wallpaper", Intent.ACTION_SET_WALLPAPER, "wallpaper", "background"),
        p("All settings", Settings.ACTION_SETTINGS, "settings"),
        c("Gestures", "gestures", "gestures", "capri settings"),
        c("Capri setup", "setup", "setup", "permissions", "onboarding"),
        c("Week grid", "classes", "classes", "timetable", "schedule", "week"),
        c("Notification access", "notifs", "notifs", "notification access"),
        c("Usage access", "usage", "usage", "screen time"),
        c("Do Not Disturb access", "quiet", "dnd", "do not disturb", "silent", "quiet"),
        c("Capri overlay", "overlay", "overlay", "mini capri"),
    )

    /** Up to [limit] pages matching [query] (2+ letters): title start, then a word start, then contains. */
    fun search(query: String, limit: Int = 4): List<SettingPage> {
        val q = query.trim().lowercase().removePrefix("open ").trim()
        if (q.length < 2) return emptyList()
        fun rank(pg: SettingPage): Int? {
            val t = pg.title.lowercase()
            return when {
                t.startsWith(q) -> 0
                pg.words.any { it.startsWith(q) } -> 1
                t.split(" ").any { it.startsWith(q) } -> 1
                t.contains(q) || pg.words.any { it.contains(q) } -> 2
                else -> null
            }
        }
        return PAGES.mapNotNull { pg -> rank(pg)?.let { pg to it } }.sortedBy { it.second }.take(limit).map { it.first }
    }

    /** Opens an Android page (falls back to the main Settings app). */
    fun open(context: Context, page: SettingPage): Boolean {
        val action = page.action ?: return false
        for (a in listOf(action, Settings.ACTION_SETTINGS)) {
            try {
                context.startActivity(Intent(a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: Exception) { }
        }
        return false
    }
}

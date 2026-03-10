package app.kitsunping.data.settings

import android.content.Context
import app.kitsunping.ui.theme.AppThemeMode

class UiSettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_UI, Context.MODE_PRIVATE)

    fun loadThemeMode(): AppThemeMode {
        val raw = prefs.getString(KEY_THEME_MODE, AppThemeMode.KITSUNPING.name).orEmpty()
        return AppThemeMode.entries.firstOrNull { it.name == raw } ?: AppThemeMode.KITSUNPING
    }

    fun saveThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    fun loadDeveloperMode(): Boolean {
        return prefs.getBoolean(KEY_DEVELOPER_MODE, false)
    }

    fun saveDeveloperMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEVELOPER_MODE, enabled).apply()
    }

    private companion object {
        private const val PREFS_UI = "kitsunping_ui"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DEVELOPER_MODE = "developer_mode"
    }
}

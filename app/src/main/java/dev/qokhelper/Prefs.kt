package dev.qokhelper

import android.content.Context

/** Each provider keeps its own key, model and base URL, so switching back and forth loses nothing. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("qok", Context.MODE_PRIVATE)

    var provider: Provider
        get() = runCatching { Provider.valueOf(sp.getString("provider", null)!!) }.getOrDefault(Provider.CLAUDE)
        set(v) = sp.edit().putString("provider", v.name).apply()

    fun settings(p: Provider) = ProviderSettings(
        apiKey = sp.getString("${p.name}.api_key", "").orEmpty(),
        model = sp.getString("${p.name}.model", null)?.takeIf { it.isNotBlank() } ?: p.defaultModel,
        baseUrl = sp.getString("${p.name}.base_url", "").orEmpty(),
    )

    fun save(p: Provider, s: ProviderSettings) {
        sp.edit()
            .putString("${p.name}.api_key", s.apiKey.trim())
            .putString("${p.name}.model", s.model.trim())
            .putString("${p.name}.base_url", s.baseUrl.trim())
            .apply()
    }
}

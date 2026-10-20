package dev.qokhelper

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("qok", Context.MODE_PRIVATE)

    var apiKey: String
        get() = sp.getString("api_key", "").orEmpty()
        set(v) = sp.edit().putString("api_key", v.trim()).apply()

    var model: String
        get() = sp.getString("model", AnswerEngine.MODELS.first()) ?: AnswerEngine.MODELS.first()
        set(v) = sp.edit().putString("model", v).apply()
}

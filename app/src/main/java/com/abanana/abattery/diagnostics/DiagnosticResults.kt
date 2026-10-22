package com.abanana.abattery.diagnostics

import android.content.Context

object DiagnosticResults {
    fun results(context: Context): Map<String, String> =
        context
            .getSharedPreferences("diagnostic_results", 0)
            .all
            .mapNotNull { (key, value) -> (value as? String)?.let { key to it } }
            .toMap()

    fun record(context: Context, test: String, result: String) {
        context
            .getSharedPreferences("diagnostic_results", 0)
            .edit()
            .putString(test, result)
            .putLong("time:$test", System.currentTimeMillis())
            .apply()
    }
}

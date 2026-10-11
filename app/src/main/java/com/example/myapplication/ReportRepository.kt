package com.example.myapplication

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local prototype storage. Reports and alerts survive app restarts. */
class ReportRepository(context: Context) {
    private val preferences = context.getSharedPreferences("lost_and_found", Context.MODE_PRIVATE)

    fun load(): ReportBook {
        val saved = preferences.getString("book", null) ?: return ReportBook()
        val root = JSONObject(saved)
        val reports = root.getJSONArray("reports")
        val alerts = root.getJSONArray("alerts")
        return ReportBook(
            reports = List(reports.length()) { index ->
                val report = reports.getJSONObject(index)
                ItemReport(
                    id = report.getString("id"),
                    kind = ReportKind.valueOf(report.getString("kind")),
                    description = report.getString("description"),
                    location = report.getString("location"),
                    photoPath = report.nullableString("photoPath"),
                    createdAt = report.getLong("createdAt"),
                    matchedLostId = report.nullableString("matchedLostId"),
                    foundReportId = report.nullableString("foundReportId"),
                )
            },
            alerts = List(alerts.length()) { index ->
                val alert = alerts.getJSONObject(index)
                ItemAlert(
                    id = alert.getString("id"),
                    lostReportId = alert.getString("lostReportId"),
                    foundReportId = alert.getString("foundReportId"),
                    createdAt = alert.getLong("createdAt"),
                    read = alert.getBoolean("read"),
                )
            },
        )
    }

    fun save(book: ReportBook) {
        val root = JSONObject().put("reports", JSONArray().apply {
            book.reports.forEach { report ->
                put(JSONObject().apply {
                    put("id", report.id)
                    put("kind", report.kind.name)
                    put("description", report.description)
                    put("location", report.location)
                    put("photoPath", report.photoPath ?: JSONObject.NULL)
                    put("createdAt", report.createdAt)
                    put("matchedLostId", report.matchedLostId ?: JSONObject.NULL)
                    put("foundReportId", report.foundReportId ?: JSONObject.NULL)
                })
            }
        }).put("alerts", JSONArray().apply {
            book.alerts.forEach { alert ->
                put(JSONObject().apply {
                    put("id", alert.id)
                    put("lostReportId", alert.lostReportId)
                    put("foundReportId", alert.foundReportId)
                    put("createdAt", alert.createdAt)
                    put("read", alert.read)
                })
            }
        })
        check(preferences.edit().putString("book", root.toString()).commit()) {
            "The report could not be saved. Please try again."
        }
    }

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else getString(key)
}

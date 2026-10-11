package com.example.myapplication

import java.util.UUID

enum class ReportKind { LOST, FOUND }

data class ItemReport(
    val id: String = UUID.randomUUID().toString(),
    val kind: ReportKind,
    val description: String,
    val location: String,
    val photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val matchedLostId: String? = null,
    val foundReportId: String? = null,
)

data class ItemAlert(
    val id: String = UUID.randomUUID().toString(),
    val lostReportId: String,
    val foundReportId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val read: Boolean = false,
)

data class ReportBook(
    val reports: List<ItemReport> = emptyList(),
    val alerts: List<ItemAlert> = emptyList(),
) {
    fun addLost(description: String, location: String, photoPath: String?): ReportBook {
        require(description.isNotBlank()) { "Describe the item you lost." }
        require(location.isNotBlank()) { "Enter where the item was lost." }
        return copy(reports = listOf(ItemReport(
            kind = ReportKind.LOST,
            description = description.trim(),
            location = location.trim(),
            photoPath = photoPath,
        )) + reports)
    }

    fun addFound(description: String, location: String, photoPath: String?, lostId: String?): ReportBook {
        require(!photoPath.isNullOrBlank()) { "Add a photo of the item you found." }
        require(location.isNotBlank()) { "Enter where the item was found." }
        val lost = lostId?.let { id ->
            reports.firstOrNull { it.id == id && it.kind == ReportKind.LOST && it.foundReportId == null }
                ?: throw IllegalArgumentException("This lost report is no longer available. Select another report.")
        }
        val found = ItemReport(
            kind = ReportKind.FOUND,
            description = description.trim(),
            location = location.trim(),
            photoPath = photoPath,
            matchedLostId = lost?.id,
        )
        return copy(
            reports = listOf(found) + reports.map {
                if (it.id == lost?.id) it.copy(foundReportId = found.id) else it
            },
            alerts = if (lost == null) alerts else listOf(ItemAlert(
                lostReportId = lost.id, foundReportId = found.id,
            )) + alerts,
        )
    }

    fun markRead(alertId: String): ReportBook = copy(
        alerts = alerts.map { if (it.id == alertId) it.copy(read = true) else it },
    )
}

package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test

class ReportBookTest {
    @Test fun lostReportRequiresDescriptionAndLocationButNotPhoto() {
        assertThrows(IllegalArgumentException::class.java) { ReportBook().addLost("  ", "Library", null) }
        assertThrows(IllegalArgumentException::class.java) { ReportBook().addLost("Keys", "  ", null) }
        val report = ReportBook().addLost("  Blue backpack  ", " Library ", null).reports.single()
        assertEquals("Blue backpack", report.description)
        assertEquals("Library", report.location)
        assertNull(report.photoPath)
    }

    @Test fun foundReportRequiresPhotoAndLocationButNotDescription() {
        assertThrows(IllegalArgumentException::class.java) { ReportBook().addFound("Keys", "Library", null, null) }
        assertThrows(IllegalArgumentException::class.java) { ReportBook().addFound("", "Library", " ", null) }
        assertThrows(IllegalArgumentException::class.java) { ReportBook().addFound("", " ", "photo.jpg", null) }
        val book = ReportBook().addFound("", "Library", "photo.jpg", null)
        assertEquals(ReportKind.FOUND, book.reports.single().kind)
        assertTrue(book.alerts.isEmpty())
    }

    @Test fun explicitMatchClosesOnlySelectedReportAndCreatesOneAlert() {
        val original = ReportBook().addLost("Keys", "Library", null).addLost("Keys", "Library", null)
        val selected = original.reports.last()
        val result = original.addFound("Silver keys", "Desk", "photo.jpg", selected.id)
        val found = result.reports.first()
        assertEquals(selected.id, found.matchedLostId)
        assertEquals(found.id, result.reports.first { it.id == selected.id }.foundReportId)
        assertNull(result.reports.first { it.id == original.reports.first().id }.foundReportId)
        assertEquals(selected.id, result.alerts.single().lostReportId)
        assertEquals(found.id, result.alerts.single().foundReportId)
        assertFalse(result.alerts.single().read)
        assertNull(original.reports.last().foundReportId)
    }

    @Test fun similarUnlinkedReportDoesNotClaimAnItemWasFound() {
        val book = ReportBook().addLost("Keys", "Library", null).addFound("Keys", "Library", "photo.jpg", null)
        assertTrue(book.alerts.isEmpty())
        assertNull(book.reports.last().foundReportId)
    }

    @Test fun staleOrDuplicateMatchIsRejectedWithoutAddingReportsOrAlerts() {
        val original = ReportBook().addLost("Keys", "Library", null)
        assertThrows(IllegalArgumentException::class.java) { original.addFound("", "Desk", "photo.jpg", "missing-id") }
        val matched = original.addFound("", "Desk", "photo.jpg", original.reports.single().id)
        assertThrows(IllegalArgumentException::class.java) { matched.addFound("", "Desk", "photo.jpg", original.reports.single().id) }
        assertThrows(IllegalArgumentException::class.java) { matched.addFound("", "Desk", "photo.jpg", matched.reports.first().id) }
        assertEquals(2, matched.reports.size)
        assertEquals(1, matched.alerts.size)
    }

    @Test fun readingAnAlertIsIdempotentAndKeepsOtherAlertsUnread() {
        val initial = ReportBook().addLost("Keys", "Library", null)
        val firstMatch = initial.addFound("", "Desk", "photo.jpg", initial.reports.single().id)
        val secondLost = firstMatch.addLost("Bag", "Lab", null)
        val book = secondLost.addFound("", "Lab", "bag.jpg", secondLost.reports.first().id)
        val alertId = book.alerts.first().id
        val read = book.markRead(alertId)
        assertTrue(read.alerts.first().read)
        assertFalse(read.alerts.last().read)
        assertEquals(read, read.markRead(alertId))
        assertEquals(book, book.markRead("missing-id"))
    }
}

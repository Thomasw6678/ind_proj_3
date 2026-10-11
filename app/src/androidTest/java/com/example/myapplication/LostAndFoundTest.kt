package com.example.myapplication

import android.content.Context
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.myapplication.ui.theme.MyApplicationTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class LostAndFoundTest {
    @get:Rule val compose = createComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = context.getSharedPreferences("lost_and_found", Context.MODE_PRIVATE)
    private var previous: String? = null
    private val repository = ReportRepository(context)
    private var photo: File? = null

    @Before fun preserveExistingReports() {
        previous = preferences.getString("book", null)
        preferences.edit().remove("book").commit()
    }

    @After fun restoreExistingReports() {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        repository.load().alerts.forEach { manager.cancel(it.id.hashCode()) }
        preferences.edit().apply {
            if (previous == null) remove("book") else putString("book", previous)
        }.commit()
        photo?.delete()
    }

    private fun launchApp(photoUri: Uri? = null) {
        val registryOwner = object : ActivityResultRegistryOwner {
            override val activityResultRegistry = object : ActivityResultRegistry() {
                override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
                    dispatchResult(requestCode, photoUri)
                }
            }
        }
        compose.setContent {
            if (photoUri == null) MyApplicationTheme { MyApplicationApp() }
            else CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                MyApplicationTheme { MyApplicationApp() }
            }
        }
    }

    private fun waitForSave(condition: (ReportBook) -> Boolean) {
        compose.waitUntil(timeoutMillis = 10000) { condition(repository.load()) }
    }

    @Test fun lostFormValidatesAndSavesWithoutPhoto() {
        launchApp()
        compose.onNodeWithText("Report a lost item").performClick()
        compose.onNodeWithText("Submit lost report").performScrollTo().performClick()
        compose.onNodeWithText("Describe the item you lost.").assertExists()
        compose.onNodeWithText("Enter the item's location.").assertExists()
        assertTrue(repository.load().reports.isEmpty())
        compose.onNodeWithText("Description (required)").performScrollTo().performTextInput("Blue backpack")
        compose.onNodeWithText("Last known location (required)").performScrollTo().performTextInput("Library")
        compose.onNodeWithText("Submit lost report").performScrollTo().performClick()
        waitForSave { it.reports.size == 1 }
        val saved = ReportRepository(context).load().reports.single()
        assertEquals("Blue backpack", saved.description)
        assertEquals("Library", saved.location)
        assertNull(saved.photoPath)
        compose.onNodeWithText("Lost & Found").assertExists()
    }

    @Test fun foundFormRejectsMissingPhotoEvenWithLocation() {
        launchApp()
        compose.onNodeWithText("Report a found item").performClick()
        compose.onNodeWithText("Found location (required)").performScrollTo().performTextInput("Student center")
        compose.onNodeWithText("Submit found report").performScrollTo().performClick()
        compose.onNodeWithText("A photo is required for a found item.").assertExists()
        assertTrue(repository.load().reports.isEmpty())
        assertTrue(repository.load().alerts.isEmpty())
    }

    @Test fun foundFormImportsPhotoAndLinksLostReport() {
        val file = File(context.cacheDir, "picker-test.png")
        photo = file
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val original = ReportBook().addLost("Blue backpack", "Library", null)
        repository.save(original)
        launchApp(Uri.fromFile(file))
        compose.onNodeWithText("I found this item").performScrollTo().performClick()
        compose.onNodeWithText("Choose a photo").performScrollTo().performClick()
        compose.waitUntil(timeoutMillis = 10000) {
            compose.onAllNodesWithText("Replace photo").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Found location (required)").performScrollTo().performTextInput("Reception desk")
        compose.onNodeWithText("Submit found report").performScrollTo().performClick()
        waitForSave { it.alerts.size == 1 }
        val saved = ReportRepository(context).load()
        val found = saved.reports.first { it.kind == ReportKind.FOUND }
        assertEquals("", found.description)
        assertEquals("Reception desk", found.location)
        assertEquals(original.reports.single().id, found.matchedLostId)
        assertEquals(found.id, saved.reports.first { it.kind == ReportKind.LOST }.foundReportId)
        val imported = File(found.photoPath!!)
        assertTrue(imported.exists())
        assertNotEquals(file.absolutePath, imported.absolutePath)
        file.delete()
        assertNotNull(android.graphics.BitmapFactory.decodeFile(imported.absolutePath))
        imported.delete()
    }
    @Test fun matchedAlertShowsPhotoAndLocationAndReadStateSurvivesReload() {
        val file = File(context.filesDir, "test-found-photo.png")
        photo = file
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val lost = ReportBook().addLost("Blue backpack", "Library", null)
        val book = lost.addFound("Blue bag", "Reception desk", file.absolutePath, lost.reports.single().id)
        repository.save(book)
        assertEquals(book, ReportRepository(context).load())
        launchApp()
        compose.onNodeWithText("Alerts").performClick()
        compose.onNodeWithText("New · item reported found").assertExists()
        compose.onNodeWithText("Found at: Reception desk").assertExists()
        compose.onNodeWithText("Blue backpack").assertExists()
        compose.onNodeWithContentDescription("Reported item photo").assertExists()
        compose.onNodeWithText("Mark as read").performScrollTo().performClick()
        waitForSave { it.alerts.single().read }
        assertTrue(ReportRepository(context).load().alerts.single().read)
        compose.onNodeWithText("Item reported found").assertExists()
    }
}

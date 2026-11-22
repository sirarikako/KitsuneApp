package com.kitsuneapp

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.kitsuneapp.backup.BackupCoordinator
import com.kitsuneapp.data.KitsuneDatabase
import com.kitsuneapp.data.Repository
import com.kitsuneapp.data.StagedSnapshot
import com.kitsuneapp.data.ThemeMode
import com.kitsuneapp.data.ThemePreferences
import com.kitsuneapp.data.newId
import com.kitsuneapp.feature.settings.DataManagementPage
import com.kitsuneapp.ui.KitsuneTheme
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class RestoreLifecycleTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: KitsuneDatabase
    private lateinit var repository: Repository
    private lateinit var backup: BackupCoordinator
    private lateinit var fixture: File
    private lateinit var model: AppViewModel

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        database = Room.inMemoryDatabaseBuilder(context, KitsuneDatabase::class.java).build()
        repository = Repository(database, newId())
        runBlocking { repository.initialize(true) }
        backup = BackupCoordinator(context, repository)
        fixture = File.createTempFile("rotation-test-", ".json", context.cacheDir)
        instrumentation.context.assets.open("backup-v1.json").use { source ->
            fixture.outputStream().use { source.copyTo(it) }
        }
        showDataPage()
        compose.waitUntil(20_000) { model.state.value.records != null }
    }

    @After
    fun tearDown() {
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        database.close()
        fixture.delete()
    }

    @Test
    fun rotatingDuringRestoreKeepsStageUntilOneSuccessfulCommit() {
        val preview = openPreview()
        val staged = preview.snapshot
        val stagingFile = stagingFile(staged)

        pausePublication {
            compose.onNodeWithText("恢复全部数据").performClick()
            waitForActiveRestore()
            rotateActivity()
            showDataPage()
            assertTrue(staged.db.isOpen)
            assertTrue(stagingFile.isFile)
            assertEquals(0L, runBlocking { repository.read().state.revision })
        }

        compose.waitUntilAtLeastOneExists(hasText("完整恢复成功"), 20_000)
        compose.waitUntil(20_000) { !model.isBusy }
        val restored = runBlocking { repository.read() }
        assertEquals(staged.info.datasetId, restored.state.datasetId)
        assertEquals(1L, restored.state.revision)
        assertEquals(staged.counts.entries, restored.entries.size)
        assertNull(model.restorePreview.value)
        assertFalse(staged.db.isOpen)
        assertFalse(stagingFile.exists())
    }

    @Test
    fun recreatingActivityDuringFailedRestoreKeepsPreviewForRetryOrDismissal() {
        runBlocking { repository.applyTheme(ThemePreferences(themeMode = ThemeMode.DARK)) }
        val before = runBlocking { repository.read() }
        compose.waitUntil(20_000) { model.state.value.records?.state == before.state }
        val preview = openPreview()
        val staged = preview.snapshot
        val stagingFile = stagingFile(staged)
        val unavailable = File(fixture.parentFile, "${newId()}/protection.json")

        pausePublication {
            compose.runOnIdle { model.restoreBackup(preview, Uri.fromFile(unavailable)) }
            waitForActiveRestore()
            compose.activityRule.scenario.recreate()
            showDataPage()
            assertTrue(staged.db.isOpen)
            assertTrue(stagingFile.isFile)
        }

        compose.waitUntil(20_000) { !model.isBusy && model.error.value != null }
        assertSame(preview, model.restorePreview.value)
        assertEquals(before, runBlocking { repository.read() })
        assertTrue(staged.db.isOpen)
        compose.waitUntilAtLeastOneExists(hasText("确认完整恢复"), 20_000)
        compose.onNodeWithText("取消").performClick()
        compose.waitUntil(20_000) { model.restorePreview.value == null && !stagingFile.exists() }
        assertFalse(staged.db.isOpen)
    }

    private fun openPreview(): RestorePreview.Local {
        compose.runOnIdle { model.previewLocalRestore(Uri.fromFile(fixture)) }
        compose.waitUntilAtLeastOneExists(hasText("确认完整恢复"), 20_000)
        compose.waitUntil(20_000) { !model.isBusy }
        return model.restorePreview.value as RestorePreview.Local
    }

    private fun showDataPage() {
        compose.activityRule.scenario.onActivity { activity ->
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(activity.application) { repository to backup } as T
            }
            val retained = ViewModelProvider(activity, factory)[AppViewModel::class.java]
            if (::model.isInitialized) assertSame(model, retained) else model = retained
            activity.setContent {
                val state by retained.state.collectAsState()
                state.records?.let { records ->
                    KitsuneTheme(records.theme) { DataManagementPage(records, retained) {} }
                }
            }
        }
    }

    private fun rotateActivity() {
        val scenario = compose.activityRule.scenario
        lateinit var original: ComponentActivity
        var orientation = Configuration.ORIENTATION_UNDEFINED
        scenario.onActivity { activity ->
            original = activity
            val toLandscape = activity.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
            orientation = if (toLandscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            activity.requestedOrientation = if (toLandscape) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        compose.waitUntil(20_000) {
            var rotated = false
            scenario.onActivity { activity ->
                rotated = activity !== original && activity.resources.configuration.orientation == orientation
            }
            rotated
        }
    }

    private fun pausePublication(test: () -> Unit) {
        runBlocking { backup.publication.lock() }
        try {
            test()
        } finally {
            backup.publication.unlock()
        }
    }

    private fun waitForActiveRestore() =
        compose.waitUntil(20_000) { model.isBusy && model.restorePreview.value == null }

    private fun stagingFile(staged: StagedSnapshot): File =
        File(requireNotNull(staged.db.openHelper.databaseName)).also { assertTrue(it.isFile) }
}

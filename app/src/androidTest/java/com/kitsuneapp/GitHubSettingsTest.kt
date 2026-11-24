package com.kitsuneapp

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.kitsuneapp.backup.BackupCoordinator
import com.kitsuneapp.backup.GitHubPauseReason
import com.kitsuneapp.backup.GitHubState
import com.kitsuneapp.backup.GitHubTarget
import com.kitsuneapp.data.KitsuneDatabase
import com.kitsuneapp.data.Repository
import com.kitsuneapp.data.newId
import com.kitsuneapp.feature.settings.GITHUB_PAUSED
import com.kitsuneapp.feature.settings.GitHubPage
import com.kitsuneapp.feature.settings.SettingsPage
import com.kitsuneapp.ui.KitsuneTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GitHubSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private lateinit var database: KitsuneDatabase
    private lateinit var repository: Repository
    private lateinit var backup: BackupCoordinator
    private lateinit var model: AppViewModel
    private lateinit var context: Context
    private lateinit var preferenceName: String
    @Volatile private var rejectNextSave = false

    @Test
    fun wifiSaveKeepsControlsAndScrollStableAndRollsBackOnFailure() {
        start()
        showSettings(githubPage = true)

        val toggle = SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState)
        val wifi = compose.onNode(toggle)
        wifi.performScrollTo().assertIsOff()
        val before = wifi.fetchSemanticsNode().boundsInRoot
        runBlocking { backup.publication.lock() }
        try {
            wifi.performClick().assertIsOn().assertIsNotEnabled()
            compose.onNodeWithText("立即备份").assertIsEnabled()
            compose.onNodeWithText("从 GitHub 恢复").assertIsEnabled()
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertCountEquals(0)
            assertEquals(before, wifi.fetchSemanticsNode().boundsInRoot)
            compose.runOnIdle { model.setGitHubWifi(checkNotNull(backup.github.state.value).binding, false) }
            wifi.assertIsOn()
        } finally {
            backup.publication.unlock()
        }
        compose.waitUntil(10_000) { backup.github.state.value?.wifiOnly == true && model.githubWifiChange.value == null }
        wifi.assertIsOn()
        assertEquals(before, wifi.fetchSemanticsNode().boundsInRoot)

        rejectNextSave = true
        wifi.performClick()
        compose.waitUntil(10_000) { model.githubWifiChange.value?.error != null }
        wifi.assertIsOn().assertIsEnabled()
        compose.onNodeWithText("无法保存 GitHub 备份状态").assertExists()

        compose.activityRule.scenario.recreate()
        showSettings(githubPage = true)
        wifi.assertIsOn()
        compose.onNodeWithText("重新配置连接").performScrollTo().performClick()
        val draftWifi = compose.onNode(toggle and isEnabled())
        draftWifi.performScrollTo().performClick().assertIsOff()
        assertEquals(true, backup.github.state.value?.wifiOnly)
    }

    @Test
    fun pauseWarningRemainsAfterAcknowledgingAndActivityRecreation() {
        start(paused = true)
        val scenario = compose.activityRule.scenario
        scenario.moveToState(Lifecycle.State.STARTED)
        showSettings(githubPage = false)
        val title = compose.onNodeWithText("GitHub 自动备份已暂停")
        title.assertDoesNotExist()
        scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(10_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("GitHub 自动备份已暂停"))
            .fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("稍后处理").performClick()
        compose.waitUntil(10_000) { backup.github.state.value?.acknowledgedPause != null && !model.isBusy }
        title.assertDoesNotExist()
        compose.onNodeWithText(GITHUB_PAUSED).assertExists()

        scenario.recreate()
        showSettings(githubPage = false)
        title.assertDoesNotExist()
        compose.onNodeWithText(GITHUB_PAUSED).assertExists()
    }

    private fun start(paused: Boolean = false) {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        val installation = newId()
        preferenceName = "github-test-$installation"
        val prefs = context.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
        val isolated = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                if (name == "github-target") object : SharedPreferences by prefs {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = prefs.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                                editor.putString(key, value)
                                return this
                            }

                            override fun commit(): Boolean {
                                if (!rejectNextSave) return editor.commit()
                                rejectNextSave = false
                                return false
                            }
                        }
                    }
                } else context.getSharedPreferences("$name-$installation", mode)
        }
        database = Room.inMemoryDatabaseBuilder(isolated, KitsuneDatabase::class.java).build()
        repository = Repository(database, installation)
        runBlocking { repository.initialize(true) }
        val local = runBlocking { repository.ownedState() }
        val config = GitHubState(installation, GitHubTarget("owner", "backup", "main"), 7L, "unused",
            wifiOnly = false, generation = local.generation, paused = null, lastRevision = local.revision)
        val saved = if (paused) config.withPause(GitHubPauseReason.CONFLICT) else config
        check(prefs.edit().putString("target", Gson().toJson(saved)).commit())
        backup = BackupCoordinator(isolated, repository)
    }

    private fun showSettings(githubPage: Boolean) {
        compose.activityRule.scenario.onActivity { activity ->
            val factory = object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AppViewModel(activity.application) { repository to backup } as T
            }
            val retained = ViewModelProvider(activity, factory)[AppViewModel::class.java]
            if (::model.isInitialized) assertSame(model, retained) else model = retained
            activity.setContent {
                val appState by retained.state.collectAsState()
                appState.records?.let { records ->
                    KitsuneTheme(records.theme) {
                        if (githubPage) GitHubPage(records, retained) {}
                        else {
                            val config by backup.github.state.collectAsState()
                            SettingsPage(config?.pause(records.state) != null) {}
                            BackupAlerts(records, retained) {}
                        }
                    }
                }
            }
        }
        compose.waitUntil(10_000) { model.state.value.records != null }
    }

    @After
    fun tearDown() {
        compose.activityRule.scenario.onActivity { it.viewModelStore.clear() }
        if (::database.isInitialized) database.close()
        if (::preferenceName.isInitialized) context.deleteSharedPreferences(preferenceName)
    }
}

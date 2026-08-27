package com.scriverse.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.scriverse.app.core.model.AiConversation
import com.scriverse.app.core.model.AiMessage
import com.scriverse.app.core.model.AiMessageRole
import com.scriverse.app.core.model.Chapter
import com.scriverse.app.core.model.ChapterType
import com.scriverse.app.core.model.KnowledgeEntry
import com.scriverse.app.core.model.KnowledgeType
import com.scriverse.app.core.model.SyncState
import com.scriverse.app.core.model.SyncSummary
import com.scriverse.app.core.model.WorkSummary
import com.scriverse.app.core.model.WorkspaceCompatibility
import com.scriverse.app.core.model.WorkspaceKind
import com.scriverse.app.core.model.WorkspaceProfile
import com.scriverse.app.core.network.ScriverseApiClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

data class AppUiState(
    val profiles: List<WorkspaceProfile> = emptyList(),
    val selectedProfileId: String? = null,
    val works: List<WorkSummary> = emptyList(),
    val selectedWorkId: String? = null,
    val chapters: List<Chapter> = emptyList(),
    val selectedChapterId: String? = null,
    val knowledge: List<KnowledgeEntry> = emptyList(),
    val conversations: List<AiConversation> = emptyList(),
    val sync: SyncSummary = SyncSummary(SyncState.IDLE, 0, 0, 0, null),
    val serverCheckMessage: String? = null,
    val isCheckingServer: Boolean = false,
    val isLoggingIn: Boolean = false,
    val loginMessage: String? = null,
    val saveState: String = "已保存到本机",
) {
    val selectedProfile get() = profiles.firstOrNull { it.id == selectedProfileId }
    val selectedWork get() = works.firstOrNull { it.id == selectedWorkId }
    val selectedChapter get() = chapters.firstOrNull { it.id == selectedChapterId }
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ScriverseApplication
    private val mutableState = MutableStateFlow(seedState())
    val state: StateFlow<AppUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val persisted = app.database.listProfiles()
            if (persisted.isNotEmpty()) {
                mutableState.update { current ->
                    current.copy(profiles = persisted, selectedProfileId = persisted.first().id)
                }
            }
        }
    }

    fun selectProfile(id: String) {
        mutableState.update { it.copy(selectedProfileId = id) }
    }

    fun selectWork(id: String) {
        mutableState.update { current -> current.copy(selectedWorkId = id) }
    }

    fun selectChapter(id: String) {
        mutableState.update { it.copy(selectedChapterId = id) }
    }

    fun updateChapter(content: String) {
        mutableState.update { current ->
            val selected = current.selectedChapterId ?: return@update current
            current.copy(
                chapters = current.chapters.map { chapter ->
                    if (chapter.id == selected) {
                        chapter.copy(
                            content = content,
                            localRevision = chapter.localRevision + 1,
                            updatedAt = Instant.now(),
                        )
                    } else {
                        chapter
                    }
                },
                saveState = "待同步",
                sync = current.sync.copy(state = SyncState.PENDING, pendingCount = current.sync.pendingCount + 1),
            )
        }
    }

    fun addRemoteWorkspace(name: String, origin: String) {
        if (name.isBlank() || origin.isBlank()) return
        mutableState.update { it.copy(isCheckingServer = true, serverCheckMessage = "正在检查 Server") }
        viewModelScope.launch {
            runCatching {
                val client = ScriverseApiClient(origin) { null }
                val health = client.health(BuildConfig.VERSION_NAME)
                val profile = WorkspaceProfile(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    kind = WorkspaceKind.REMOTE,
                    origin = origin,
                    compatibility = health.compatibility,
                    userId = null,
                    createdAt = Instant.now(),
                    lastUsedAt = Instant.now(),
                )
                app.database.upsertProfile(profile, null)
                profile
            }.onSuccess { profile ->
                mutableState.update { current ->
                    current.copy(
                        profiles = listOf(profile) + current.profiles,
                        selectedProfileId = profile.id,
                        isCheckingServer = false,
                        serverCheckMessage = when (profile.compatibility) {
                            WorkspaceCompatibility.COMPATIBLE -> "Server 兼容，可以登录"
                            WorkspaceCompatibility.ONLINE_ONLY -> "Server 可在线使用，需升级后支持原生协议"
                            else -> "Server 返回了不兼容的协议"
                        },
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        isCheckingServer = false,
                        serverCheckMessage = error.message ?: "Server 检查失败",
                    )
                }
            }
        }
    }

    fun createLocalWorkspace() {
        val profile = WorkspaceProfile(
            id = UUID.randomUUID().toString(),
            name = "本机工作区",
            kind = WorkspaceKind.LOCAL,
            origin = null,
            compatibility = WorkspaceCompatibility.CHECKING,
            userId = null,
            createdAt = Instant.now(),
            lastUsedAt = Instant.now(),
        )
        viewModelScope.launch { app.database.upsertProfile(profile, null) }
        mutableState.update { it.copy(profiles = listOf(profile) + it.profiles, selectedProfileId = profile.id) }
    }

    fun login(profileId: String, username: String, password: CharArray, captcha: String) {
        val profile = mutableState.value.profiles.firstOrNull { it.id == profileId } ?: return
        val origin = profile.origin ?: return
        if (username.isBlank() || password.isEmpty() || captcha.isBlank()) return
        mutableState.update { it.copy(isLoggingIn = true, loginMessage = "正在登录") }
        viewModelScope.launch {
            val passwordString = password.concatToString()
            password.fill('\u0000')
            val payload = JSONObject()
                .put("username", username.trim())
                .put("password", passwordString)
                .put("captcha", captcha.trim())
                .put("clientId", getApplication<Application>().packageName)
                .put("profileId", profile.id)
                .put("platform", "android")
                .put("clientVersion", BuildConfig.VERSION_NAME)
            runCatching {
                val response = ScriverseApiClient(origin) { null }.login(payload)
                payload.remove("password")
                val data = response.getJSONObject("data")
                val token = data.getString("token").toCharArray()
                val encrypted = app.secretVault.encrypt(token)
                val userId = data.getJSONObject("user").getString("id")
                val loggedIn = profile.copy(userId = userId, lastUsedAt = Instant.now())
                app.database.upsertProfile(loggedIn, encrypted)
                loggedIn
            }.onSuccess { loggedIn ->
                mutableState.update { current ->
                    current.copy(
                        profiles = current.profiles.map { if (it.id == loggedIn.id) loggedIn else it },
                        selectedProfileId = loggedIn.id,
                        isLoggingIn = false,
                        loginMessage = "登录成功",
                    )
                }
            }.onFailure { error ->
                payload.remove("password")
                mutableState.update {
                    it.copy(isLoggingIn = false, loginMessage = error.message ?: "登录失败")
                }
            }
        }
    }

    private fun seedState(): AppUiState {
        val now = Instant.now()
        val local = WorkspaceProfile(
            id = "local-demo",
            name = "本机创作",
            kind = WorkspaceKind.LOCAL,
            origin = null,
            compatibility = WorkspaceCompatibility.CHECKING,
            userId = "local-owner",
            createdAt = now,
            lastUsedAt = now,
        )
        val work = WorkSummary(
            id = "work-demo",
            title = "雾海来信",
            author = "本机作者",
            coverUrl = null,
            chapterCount = 18,
            wordCount = 42_680,
            targetWordCount = 120_000,
            updatedAt = now,
            role = "所有者",
        )
        val chapters = listOf(
            Chapter("chapter-1", work.id, null, "第一章 潮汐站", SAMPLE_CHAPTER, ChapterType.CHAPTER, 1, 7, 2, now),
            Chapter("chapter-2", work.id, null, "第二章 无人回信", "夜班列车穿过盐雾。", ChapterType.CHAPTER, 2, 3, 0, now),
            Chapter("chapter-3", work.id, null, "第三章 灯塔之外", "", ChapterType.CHAPTER, 3, 0, 0, now),
        )
        val knowledge = listOf(
            KnowledgeEntry("k1", work.id, KnowledgeType.CHARACTER, "林见潮", "港口电台的夜班编辑。", listOf("主角", "电台"), true, 4),
            KnowledgeEntry("k2", work.id, KnowledgeType.SETTING, "雾海潮汐站", "只在退潮时出现的废弃站台。", listOf("地点"), true, 2),
            KnowledgeEntry("k3", work.id, KnowledgeType.FORESHADOWING, "第七码头的灯", "每逢无月夜会提前熄灭。", listOf("伏笔"), false, 1),
            KnowledgeEntry("k4", work.id, KnowledgeType.TIMELINE, "旧历 47 年", "第一次海雾封港。", listOf("时间线"), false, 3),
        )
        val conversation = AiConversation(
            id = "ai-1",
            workId = work.id,
            title = "检查本章节奏",
            model = "写作助手",
            favorite = true,
            messages = listOf(
                AiMessage("m1", AiMessageRole.USER, "检查第一章节奏，并指出可以收紧的段落。", null, emptyList(), null, now),
                AiMessage("m2", AiMessageRole.ASSISTANT, "开场意象清晰。建议将第二段的背景解释后移，让来信先成为读者的第一个问题。", null, listOf("第一章第 2 段"), null, now),
            ),
            updatedAt = now,
        )
        return AppUiState(
            profiles = listOf(local),
            selectedProfileId = local.id,
            works = listOf(work),
            selectedWorkId = work.id,
            chapters = chapters,
            selectedChapterId = chapters.first().id,
            knowledge = knowledge,
            conversations = listOf(conversation),
        )
    }

    private companion object {
        val SAMPLE_CHAPTER = """
            清晨四点，雾从防波堤外翻进港口。

            林见潮把最后一封没有署名的来信放到灯下。纸页被海水浸过，边缘留着细小的盐粒，地址却写得很清楚：潮汐站，第七码头。

            那座站台已经停用十七年。
        """.trimIndent()
    }
}

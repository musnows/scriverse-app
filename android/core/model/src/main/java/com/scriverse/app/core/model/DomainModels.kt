package com.scriverse.app.core.model

import java.time.Instant

enum class WorkspaceKind { LOCAL, REMOTE }

enum class WorkspaceCompatibility {
    CHECKING,
    COMPATIBLE,
    ONLINE_ONLY,
    UPGRADE_REQUIRED,
    INCOMPATIBLE,
    OFFLINE,
}

data class WorkspaceProfile(
    val id: String,
    val name: String,
    val kind: WorkspaceKind,
    val origin: String?,
    val compatibility: WorkspaceCompatibility,
    val userId: String?,
    val createdAt: Instant,
    val lastUsedAt: Instant,
)

data class NativeClientProtocol(
    val minimumAppVersion: String,
    val shellProtocol: Int,
    val syncProtocol: Int,
    val serverVersion: String,
)

data class UserSummary(
    val id: String,
    val username: String,
    val displayName: String,
    val avatarUrl: String?,
    val isAdministrator: Boolean,
)

data class WorkSummary(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String?,
    val chapterCount: Int,
    val wordCount: Long,
    val targetWordCount: Long?,
    val updatedAt: Instant,
    val role: String,
)

enum class ChapterType { CHAPTER, VOLUME, CUSTOM, APPENDIX }

data class Chapter(
    val id: String,
    val workId: String,
    val parentId: String?,
    val title: String,
    val content: String,
    val type: ChapterType,
    val order: Int,
    val serverVersion: Long,
    val localRevision: Long,
    val updatedAt: Instant,
)

enum class KnowledgeType {
    IDEA,
    SETTING,
    CHARACTER,
    RACE,
    ORGANIZATION,
    TIMELINE,
    OUTLINE,
    FORESHADOWING,
    RELATIONSHIP,
    ATTACHMENT,
}

data class KnowledgeEntry(
    val id: String,
    val workId: String,
    val type: KnowledgeType,
    val title: String,
    val body: String,
    val tags: List<String>,
    val pinned: Boolean,
    val version: Long,
)

enum class SyncState { IDLE, SYNCING, PENDING, CONFLICT, REJECTED, OFFLINE }

data class SyncSummary(
    val state: SyncState,
    val pendingCount: Int,
    val conflictCount: Int,
    val rejectedCount: Int,
    val lastSyncedAt: Instant?,
)

data class Mutation(
    val id: String,
    val profileId: String,
    val userId: String,
    val workId: String,
    val entityType: String,
    val entityId: String,
    val baseVersion: Long,
    val payload: String,
    val createdAt: Instant,
)

data class Conflict(
    val id: String,
    val profileId: String,
    val userId: String,
    val workId: String,
    val entityType: String,
    val entityId: String,
    val basePayload: String,
    val localPayload: String,
    val serverPayload: String,
    val createdAt: Instant,
)

enum class AiMessageRole { SYSTEM, USER, ASSISTANT, TOOL }

data class AiMessage(
    val id: String,
    val role: AiMessageRole,
    val content: String,
    val reasoning: String?,
    val citations: List<String>,
    val toolName: String?,
    val createdAt: Instant,
)

data class AiConversation(
    val id: String,
    val workId: String?,
    val title: String,
    val model: String,
    val favorite: Boolean,
    val messages: List<AiMessage>,
    val updatedAt: Instant,
)

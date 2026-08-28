package com.scriverse.app.core.database

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.scriverse.app.core.model.Chapter
import com.scriverse.app.core.model.ChapterType
import com.scriverse.app.core.model.Conflict
import com.scriverse.app.core.model.Mutation
import com.scriverse.app.core.model.WorkspaceCompatibility
import com.scriverse.app.core.model.WorkspaceKind
import com.scriverse.app.core.model.WorkspaceProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

class ScriverseDatabase(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    SCHEMA_VERSION,
) {
    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE profiles (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                kind TEXT NOT NULL CHECK (kind IN ('LOCAL', 'REMOTE')),
                origin TEXT,
                compatibility TEXT NOT NULL,
                user_id TEXT,
                encrypted_token TEXT,
                created_at TEXT NOT NULL,
                last_used_at TEXT NOT NULL,
                UNIQUE(origin, user_id)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE works (
                profile_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                id TEXT NOT NULL,
                payload TEXT NOT NULL,
                server_version INTEGER NOT NULL DEFAULT 0,
                updated_at TEXT NOT NULL,
                PRIMARY KEY (profile_id, user_id, id),
                FOREIGN KEY (profile_id) REFERENCES profiles(id) ON DELETE CASCADE
            ) WITHOUT ROWID
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE entities (
                profile_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                work_id TEXT NOT NULL,
                entity_type TEXT NOT NULL,
                entity_id TEXT NOT NULL,
                payload TEXT NOT NULL,
                base_payload TEXT NOT NULL,
                server_version INTEGER NOT NULL,
                local_revision INTEGER NOT NULL,
                updated_at TEXT NOT NULL,
                PRIMARY KEY (profile_id, user_id, entity_type, entity_id),
                FOREIGN KEY (profile_id, user_id, work_id)
                    REFERENCES works(profile_id, user_id, id) ON DELETE CASCADE
            ) WITHOUT ROWID
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE outbox (
                mutation_id TEXT PRIMARY KEY NOT NULL,
                profile_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                work_id TEXT NOT NULL,
                entity_type TEXT NOT NULL CHECK (entity_type IN ('chapter', 'setting')),
                entity_id TEXT NOT NULL,
                base_version INTEGER NOT NULL,
                payload TEXT NOT NULL,
                attempt_count INTEGER NOT NULL DEFAULT 0,
                next_attempt_at TEXT,
                created_at TEXT NOT NULL,
                UNIQUE(profile_id, user_id, entity_type, entity_id),
                FOREIGN KEY (profile_id, user_id, work_id)
                    REFERENCES works(profile_id, user_id, id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE conflicts (
                id TEXT PRIMARY KEY NOT NULL,
                profile_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                work_id TEXT NOT NULL,
                entity_type TEXT NOT NULL,
                entity_id TEXT NOT NULL,
                base_payload TEXT NOT NULL,
                local_payload TEXT NOT NULL,
                server_payload TEXT NOT NULL,
                created_at TEXT NOT NULL,
                UNIQUE(profile_id, user_id, entity_type, entity_id),
                FOREIGN KEY (profile_id, user_id, work_id)
                    REFERENCES works(profile_id, user_id, id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE sync_meta (
                profile_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                work_id TEXT NOT NULL,
                cursor TEXT,
                snapshot_id TEXT,
                last_synced_at TEXT,
                state TEXT NOT NULL DEFAULT 'IDLE',
                PRIMARY KEY (profile_id, user_id, work_id),
                FOREIGN KEY (profile_id, user_id, work_id)
                    REFERENCES works(profile_id, user_id, id) ON DELETE CASCADE
            ) WITHOUT ROWID
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE media_manifest (
                profile_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                work_id TEXT NOT NULL,
                media_id TEXT NOT NULL,
                content_hash TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                byte_length INTEGER NOT NULL CHECK (byte_length >= 0),
                local_path TEXT NOT NULL,
                PRIMARY KEY (profile_id, user_id, media_id),
                FOREIGN KEY (profile_id, user_id, work_id)
                    REFERENCES works(profile_id, user_id, id) ON DELETE CASCADE
            ) WITHOUT ROWID
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX outbox_ready_idx ON outbox(profile_id, user_id, next_attempt_at)")
        db.execSQL("CREATE INDEX entities_work_idx ON entities(profile_id, user_id, work_id, entity_type)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        require(oldVersion <= newVersion)
        // schema 1 是首个向前兼容版本，后续迁移必须逐版本追加，禁止破坏性重建。
    }

    suspend fun upsertProfile(profile: WorkspaceProfile, encryptedToken: String?) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("id", profile.id)
            put("name", profile.name)
            put("kind", profile.kind.name)
            put("origin", profile.origin)
            put("compatibility", profile.compatibility.name)
            put("user_id", profile.userId)
            put("encrypted_token", encryptedToken)
            put("created_at", profile.createdAt.toString())
            put("last_used_at", profile.lastUsedAt.toString())
        }
        writableDatabase.insertWithOnConflict(
            "profiles",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    suspend fun listProfiles(): List<WorkspaceProfile> = withContext(Dispatchers.IO) {
        readableDatabase.query(
            "profiles",
            PROFILE_COLUMNS,
            null,
            null,
            null,
            null,
            "last_used_at DESC",
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toProfile()) } }
    }

    suspend fun saveChapterAndQueue(
        profileId: String,
        userId: String,
        chapter: Chapter,
        mutation: Mutation,
    ) = withContext(Dispatchers.IO) {
        require(mutation.entityType == "chapter")
        val db = writableDatabase
        db.beginTransaction()
        try {
            val entityValues = ContentValues().apply {
                put("profile_id", profileId)
                put("user_id", userId)
                put("work_id", chapter.workId)
                put("entity_type", "chapter")
                put("entity_id", chapter.id)
                put("payload", chapter.content)
                put("base_payload", chapter.content)
                put("server_version", chapter.serverVersion)
                put("local_revision", chapter.localRevision)
                put("updated_at", chapter.updatedAt.toString())
            }
            db.insertWithOnConflict("entities", null, entityValues, SQLiteDatabase.CONFLICT_REPLACE)
            upsertMutation(db, mutation)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    suspend fun pendingMutations(profileId: String, userId: String, limit: Int = 20): List<Mutation> =
        withContext(Dispatchers.IO) {
            require(limit in 1..20)
            readableDatabase.query(
                "outbox",
                MUTATION_COLUMNS,
                "profile_id = ? AND user_id = ? AND (next_attempt_at IS NULL OR next_attempt_at <= ?)",
                arrayOf(profileId, userId, Instant.now().toString()),
                null,
                null,
                "created_at ASC",
                limit.toString(),
            ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.toMutation()) } }
        }

    suspend fun recordConflict(conflict: Conflict) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("id", conflict.id)
            put("profile_id", conflict.profileId)
            put("user_id", conflict.userId)
            put("work_id", conflict.workId)
            put("entity_type", conflict.entityType)
            put("entity_id", conflict.entityId)
            put("base_payload", conflict.basePayload)
            put("local_payload", conflict.localPayload)
            put("server_payload", conflict.serverPayload)
            put("created_at", conflict.createdAt.toString())
        }
        writableDatabase.insertWithOnConflict("conflicts", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    suspend fun deleteProfile(profileId: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete("profiles", "id = ?", arrayOf(profileId))
    }

    suspend fun integrityCheck(): DatabaseIntegrity = withContext(Dispatchers.IO) {
        val db = readableDatabase
        val integrity = db.rawQuery("PRAGMA integrity_check", null).use {
            it.moveToFirst() && it.getString(0) == "ok"
        }
        val foreignKeyErrors = db.rawQuery("PRAGMA foreign_key_check", null).use { it.count }
        DatabaseIntegrity(integrity, foreignKeyErrors)
    }

    private fun upsertMutation(db: SQLiteDatabase, mutation: Mutation) {
        val values = ContentValues().apply {
            put("mutation_id", mutation.id)
            put("profile_id", mutation.profileId)
            put("user_id", mutation.userId)
            put("work_id", mutation.workId)
            put("entity_type", mutation.entityType)
            put("entity_id", mutation.entityId)
            put("base_version", mutation.baseVersion)
            put("payload", mutation.payload)
            put("created_at", mutation.createdAt.toString())
        }
        db.insertWithOnConflict("outbox", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun Cursor.toProfile() = WorkspaceProfile(
        id = getString(getColumnIndexOrThrow("id")),
        name = getString(getColumnIndexOrThrow("name")),
        kind = WorkspaceKind.valueOf(getString(getColumnIndexOrThrow("kind"))),
        origin = getString(getColumnIndexOrThrow("origin")),
        compatibility = WorkspaceCompatibility.valueOf(getString(getColumnIndexOrThrow("compatibility"))),
        userId = getString(getColumnIndexOrThrow("user_id")),
        createdAt = Instant.parse(getString(getColumnIndexOrThrow("created_at"))),
        lastUsedAt = Instant.parse(getString(getColumnIndexOrThrow("last_used_at"))),
    )

    private fun Cursor.toMutation() = Mutation(
        id = getString(getColumnIndexOrThrow("mutation_id")),
        profileId = getString(getColumnIndexOrThrow("profile_id")),
        userId = getString(getColumnIndexOrThrow("user_id")),
        workId = getString(getColumnIndexOrThrow("work_id")),
        entityType = getString(getColumnIndexOrThrow("entity_type")),
        entityId = getString(getColumnIndexOrThrow("entity_id")),
        baseVersion = getLong(getColumnIndexOrThrow("base_version")),
        payload = getString(getColumnIndexOrThrow("payload")),
        createdAt = Instant.parse(getString(getColumnIndexOrThrow("created_at"))),
    )

    companion object {
        const val DATABASE_NAME = "scriverse_cache.db"
        const val SCHEMA_VERSION = 1
        private val PROFILE_COLUMNS = arrayOf(
            "id", "name", "kind", "origin", "compatibility", "user_id", "created_at", "last_used_at",
        )
        private val MUTATION_COLUMNS = arrayOf(
            "mutation_id", "profile_id", "user_id", "work_id", "entity_type", "entity_id",
            "base_version", "payload", "created_at",
        )
    }
}

data class DatabaseIntegrity(val integrityOk: Boolean, val foreignKeyErrors: Int) {
    val isHealthy: Boolean get() = integrityOk && foreignKeyErrors == 0
}

fun Chapter.blankFor(workId: String, id: String, title: String): Chapter = Chapter(
    id = id,
    workId = workId,
    parentId = null,
    title = title,
    content = "",
    type = ChapterType.CHAPTER,
    order = 0,
    serverVersion = 0,
    localRevision = 0,
    updatedAt = Instant.now(),
)

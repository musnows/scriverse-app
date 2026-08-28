package com.scriverse.app.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriverseDatabaseTest {
    private lateinit var context: Context
    private lateinit var database: ScriverseDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(ScriverseDatabase.DATABASE_NAME)
        database = ScriverseDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(ScriverseDatabase.DATABASE_NAME)
    }

    @Test
    fun freshSchemaPassesIntegrityAndForeignKeyChecks() = runBlocking {
        database.writableDatabase
        val result = database.integrityCheck()

        assertThat(result.isHealthy).isTrue()
        assertThat(result.foreignKeyErrors).isEqualTo(0)
    }
}

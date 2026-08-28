package com.scriverse.app

import android.app.Application
import com.scriverse.app.core.database.ScriverseDatabase
import com.scriverse.app.core.security.SecretVault

class ScriverseApplication : Application() {
    val database by lazy { ScriverseDatabase(this) }
    val secretVault by lazy { SecretVault() }
}

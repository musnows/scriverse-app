plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.scriverse.app.core.security"
    compileSdk = 37
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
}

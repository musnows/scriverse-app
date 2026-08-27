plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.scriverse.app.core.runtime"
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    defaultConfig {
        minSdk = 29
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
            }
        }
        ndk {
            abiFilters += setOf("arm64-v8a", "x86_64")
        }
    }

    buildFeatures {
        aidl = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":core:security"))
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
}

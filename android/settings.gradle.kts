pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ScriverseAndroid"

include(":app")
include(":core:model")
include(":core:network")
include(":core:database")
include(":core:security")
include(":core:runtime")
include(":core:designsystem")

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
rootProject.name = "test-app-android"
include(":app")
includeBuild("../../bv-push-sdk") {
    dependencySubstitution {
        substitute(module("com.bazaarvoice:bv-garner-sdk-android")).using(project(":sdk"))
    }
}

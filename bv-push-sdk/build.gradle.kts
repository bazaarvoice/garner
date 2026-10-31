// Root build: applies the mock-ban gate to every subproject.
plugins {
    alias(libs.plugins.kover) apply false
}

subprojects {
    // Fail the build if a banned mocking/doubles dependency or source pattern
    // appears anywhere: tests run against real objects only.
    tasks.register("mockBanCheck") {
        group = "verification"
        description = "Fails on banned mock/fake/stub dependencies and source patterns."
        doLast {
            val bannedDeps = listOf(
                "mockk", "mockito", "mockito-kotlin", "mockwebserver",
                "wiremock", "robolectric", "ktor-client-mock"
            )
            val found = mutableListOf<String>()
            configurations.matching { it.isCanBeResolved }.all {
                incoming.dependencies.forEach { dep ->
                    val name = (dep.group ?: "") + ":" + dep.name
                    if (bannedDeps.any { name.contains(it, ignoreCase = true) }) {
                        found += "${project.path}: $name"
                    }
                }
            }
            val bannedPatterns = listOf(
                "MockEngine", "MockWebServer", "WireMock", "Robolectric",
                "mockk", "Mockito", "simctl push", "sendUserNotification"
            )
            fileTree(projectDir) {
                include("src/**/*.kt", "src/**/*.swift")
            }.forEach { file ->
                val text = file.readText()
                bannedPatterns.forEach { pattern ->
                    if (text.contains(pattern)) found += "${project.path}: ${file.name} contains '$pattern'"
                }
            }
            if (found.isNotEmpty()) {
                throw GradleException("Mock ban violated:\n" + found.joinToString("\n"))
            }
        }
    }
    tasks.matching { it.name == "check" }.configureEach {
        dependsOn("mockBanCheck")
    }
}

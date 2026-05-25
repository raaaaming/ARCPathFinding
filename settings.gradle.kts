pluginManagement {
    plugins {
        kotlin("jvm") version "2.3.21"
        id("com.google.devtools.ksp") version "2.3.7"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "ARCPathFinding"
include("api", "test")

pluginManagement {
    repositories {
        maven(url = "https://s01.oss.sonatype.org/content/groups/public/")
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "pkl-package-docs"

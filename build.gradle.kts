plugins {
    kotlin("jvm") version libs.versions.kotlin
    alias(libs.plugins.pkl)
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlinSerialization)
}

group = "org.pkl-lang"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.pklDoc)
    implementation(libs.pklCommonsCli)
    implementation(libs.ktorClientCore)
    implementation(libs.ktorClientJava)
    implementation(libs.kotlinxSerializationJson)
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

val repos = arrayOf(
    "apple/pkl-pantry",
    "apple/pkl-k8s",
    "apple/pkl-go",
    "apple/pkl-swift"
)

tasks.create<JavaExec>("generateAndPublishDocs") {
    group = "publish"
    configureBuild(doPublish = true)
}

val generateDocs by tasks.registering(JavaExec::class) {
    group = "build"
    configureBuild(doPublish = false)
}

fun JavaExec.configureBuild(doPublish: Boolean) {
    val outputDir = file("build/package-docs")
    val gitRootDir = file(".")
    mainClass.set("org.pkl.package_docs.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    args = buildList {
        add(gitRootDir.absolutePath)
        add(outputDir.absolutePath)
        add(if (doPublish) "1" else "0")
        addAll(repos)
    }
}

val originalRemoteName = System.getenv("PKL_ORIGINAL_REMOTE_NAME") ?: "origin"

spotless {
    ratchetFrom = "$originalRemoteName/main"
    kotlin {
        licenseHeader("""
            /**
             * Copyright © ${'$'}YEAR Apple Inc. and the Pkl project authors. All rights reserved.
             *
             * Licensed under the Apache License, Version 2.0 (the "License");
             * you may not use this file except in compliance with the License.
             * You may obtain a copy of the License at
             *
             *     https://www.apache.org/licenses/LICENSE-2.0
             *
             * Unless required by applicable law or agreed to in writing, software
             * distributed under the License is distributed on an "AS IS" BASIS,
             * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
             * See the License for the specific language governing permissions and
             * limitations under the License.
             */
        """.trimIndent())
    }
    format("pkl") {
        licenseHeader("""
            //===----------------------------------------------------------------------===//
            // Copyright © ${'$'}YEAR Apple Inc. and the Pkl project authors. All rights reserved.
            //
            // Licensed under the Apache License, Version 2.0 (the "License");
            // you may not use this file except in compliance with the License.
            // You may obtain a copy of the License at
            //
            //     https://www.apache.org/licenses/LICENSE-2.0
            //
            // Unless required by applicable law or agreed to in writing, software
            // distributed under the License is distributed on an "AS IS" BASIS,
            // WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
            // See the License for the specific language governing permissions and
            // limitations under the License.
            //===----------------------------------------------------------------------===//
        """.trimIndent(), "(/// |/\\*\\*|module |import |amends |(\\w+))")
        target("**/*.pkl", "**/PklProject")
    }
}

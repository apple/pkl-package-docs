/*
 * Copyright © 2026 Apple Inc. and the Pkl project authors. All rights reserved.
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
plugins {
  kotlin("jvm") version libs.versions.kotlin
  alias(libs.plugins.pkl)
  alias(libs.plugins.spotless)
  alias(libs.plugins.kotlinSerialization)
}

group = "org.pkl-lang"

version = "1.0-SNAPSHOT"

fun isDynamicVersion(version: String) =
    version.endsWith("+") ||
        version.startsWith("latest.") ||
        Regex("""[\[\](),]""").containsMatchIn(version)

configurations {
  all {
    resolutionStrategy {
      eachDependency {
        if (requested.group != "org.pkl-lang" && isDynamicVersion(requested.version.toString())) {
          throw GradleException(
              "Dynamic version '${requested.version}' is not allowed for ${requested.group}:${requested.name}"
          )
        }
      }
    }
  }
}

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

val repos =
    arrayOf(
        "apple/pkl-pantry",
        "apple/pkl-k8s",
        "apple/pkl-go",
        "apple/pkl-swift",
        "apple/pkl-readers",
    )

val generateAndPublishDocs =
    tasks.register<JavaExec>("generateAndPublishDocs") {
      group = "publish"
      configureBuild(doPublish = true)
    }

val generateDocs =
    tasks.register<JavaExec>("generateDocs") {
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

val licenseHeaderStarBlock =
    $$"""
    /*
     * Copyright © $YEAR Apple Inc. and the Pkl project authors. All rights reserved.
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
    """
        .trimIndent()

spotless {
  ratchetFrom = "$originalRemoteName/main"
  kotlin {
    licenseHeader(licenseHeaderStarBlock)
    ktfmt(libs.versions.ktfmt.get())
  }
  kotlinGradle {
    licenseHeader(licenseHeaderStarBlock, "([a-zA-Z]|@file|//)")
    ktfmt(libs.versions.ktfmt.get())
  }
  format("pkl") {
    licenseHeader(
        $$"""
        //===----------------------------------------------------------------------===//
        // Copyright © $YEAR Apple Inc. and the Pkl project authors. All rights reserved.
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
        """
            .trimIndent(),
        "(/// |/\\*\\*|module |import |amends |(\\w+))",
    )
    target("**/*.pkl", "**/PklProject")
  }
}

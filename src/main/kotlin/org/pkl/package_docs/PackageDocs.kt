/**
 * Copyright © 2024 Apple Inc. and the Pkl project authors. All rights reserved.
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
package org.pkl.package_docs

import org.pkl.commons.cli.CliException
import org.pkl.core.Evaluator
import org.pkl.core.ModuleSchema
import org.pkl.core.ModuleSource
import org.pkl.core.PklInfo
import org.pkl.core.Release
import org.pkl.core.SecurityManagers
import org.pkl.core.Version
import org.pkl.core.packages.DependencyMetadata
import org.pkl.core.packages.PackageAssetUri
import org.pkl.core.packages.PackageLoadError
import org.pkl.core.packages.PackageResolver
import org.pkl.core.packages.PackageUri
import org.pkl.core.util.IoUtils
import org.pkl.doc.DocGenerator
import org.pkl.doc.DocPackageInfo
import org.pkl.doc.DocsiteInfo
import java.net.URI
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.TimeUnit

class PackageDocException(message: String) : Exception(message)

class PackageDocs(
  private val gitRootDir: Path,
  private val docsOutputDir: Path,
  private val repos: List<Repo>
) {

  private val github = GitHubClient(System.getenv("GH_TOKEN"))

  private fun discoverPackages(): List<PackageUri> {
    val allPackages = buildList {
      for (repo in repos) {
        val releases = github.getReleases(repo)
          .filter { it.name.contains('@') }
        for (release in releases) {
          if (repo.owner == "apple") {
            add(PackageUri("package://pkg.pkl-lang.org/${repo.name}/${release.name}"))
          } else {
            add(PackageUri("package://pkg.pkl-lang.org/gh/${repo.owner}/${repo.name}/${release.name}"))
          }
        }
      }
    }
    // get the latest release of every package
    return allPackages
      .groupBy { it.pathWithoutVersion }
      .values
      .map { it.sortedBy(PackageUri::getVersion).last() }
  }

  fun generateDocs() {
    val packages = discoverPackages()
    val packageSchemas = packages.mapNotNull(::getSchemasFromPackage).toMap()
    // TODO: handle errors and change import resolver signature to `(URI) -> ModuleSchema?` in Pkldoc API
    val importResolver: (URI) -> ModuleSchema =
      { uri: URI -> evaluator.evaluateSchema(ModuleSource.uri(uri)) }
    downloadExistingDocs()
    DocGenerator(
      docsiteInfo,
      mapOf(getStdlibSchemas()) + packageSchemas,
      importResolver,
      { v1, v2 -> Version.parse(v1).compareTo(Version.parse(v2)) },
      docsOutputDir
    ).run()
    println("Wrote docs to $docsOutputDir")
    print(packages.joinToString("\n"))
  }

  fun uploadDocs() {
    runCommand(listOf("git", "add", "--all", "."), docsOutputDir)
    val diff = runCommandAllowingError(listOf("git", "diff-index", "--quiet", "HEAD"), docsOutputDir)
    if (diff.exitValue == 0) {
      println("No new docs to upload")
      return
    }

    runCommand(listOf("git", "config", "--global", "user.email", "pkl-oss@group.apple.com"), docsOutputDir)
    runCommand(listOf("git", "config", "--global", "user.name", "Pkl CI"), docsOutputDir)
    runCommand(listOf("git", "commit", "-m", "Publish new documentation [skip ci]"), docsOutputDir)
    runCommand(listOf("git", "push", "origin", "www"), docsOutputDir)
  }

  private fun getStdlibSchemas(): Pair<DocPackageInfo, List<ModuleSchema>> {
    val release = Release.current()
    val schemas = release.standardLibrary().modules()
      .map { ModuleSource.uri(it) }
      .map(evaluator::evaluateSchema)
    return stdlibDocPackageInfo to schemas
  }

  private val stdlibDocPackageInfo by lazy {
    val source = ModuleSource.modulePath("org/pkl/core/stdlib/doc-package-info.pkl")
    DocPackageInfo.fromPkl(evaluator.evaluate(source))
  }

  private val docsiteInfo: DocsiteInfo by lazy {
    val source = ModuleSource.modulePath("org/pkl/package_docs/docsite-info.pkl")
    DocsiteInfo.fromPkl(evaluator.evaluate(source))
  }

  private fun downloadExistingDocs() {
    docsOutputDir.toFile().deleteRecursively()
    runCommand(listOf("git", "worktree", "prune"), gitRootDir)
    runCommand(listOf("git", "fetch", "origin", "+www:www"), gitRootDir)
    runCommand(listOf("git", "worktree", "add", docsOutputDir.toString(), "www"), gitRootDir)
  }

  private data class ProcessResult(val stdout: String, val stderr: String, val exitValue: Int)

  private fun runCommand(command: List<String>, workingDir: Path): ProcessResult {
    val result = runCommandAllowingError(command, workingDir)
    if (result.exitValue != 0) {
      throw PackageDocException("Failed to run command: ${command.joinToString(" ")}. Got stdout: ${result.stdout}, and stderr: ${result.stderr}")
    }
    return result
  }

  private fun runCommandAllowingError(command: List<String>, workingDir: Path): ProcessResult {
    val process = Runtime.getRuntime().exec(command.toTypedArray(), null, workingDir.toFile())
    val stdout = process.inputStream.reader().use { it.readText() }
    val stderr = process.errorStream.reader().use { it.readText() }
    // Note that this timeout offers little protection against the process running for too long
    // because we only get here if/when stdout and stderr are closed.
    process.waitFor(10, TimeUnit.SECONDS)
    return ProcessResult(stdout, stderr, process.exitValue())
  }

  private val packageResolver by lazy {
    PackageResolver.getInstance(SecurityManagers.defaultManager, IoUtils.getDefaultModuleCacheDir())
  }

  private val currentPklRelease = Release.current().version().toString()

  private val stdlibDependency = DocPackageInfo.PackageDependency(
    name = "pkl",
    uri = null,
    version = currentPklRelease,
    sourceCode = URI(Release.current().sourceCode().homepage()),
    sourceCodeUrlScheme = Release.current().sourceCode().sourceCodeUrlScheme,
    documentation = URI(PklInfo.current().packageIndex.getPackagePage("pkl", currentPklRelease))
  )

  private fun DependencyMetadata.getPackageDependencies(): List<DocPackageInfo.PackageDependency> {
    return buildList {
      for ((_, dependency) in dependencies) {
        val metadata = try {
          packageResolver.getDependencyMetadata(dependency.packageUri, dependency.checksums)
        } catch (e: Exception) {
          throw CliException("Failed to fetch dependency metadata for ${dependency.packageUri}: ${e.message}")
        }
        val packageDependency = DocPackageInfo.PackageDependency(
          name = metadata.name,
          uri = dependency.packageUri.uri,
          version = metadata.version.toString(),
          sourceCode = metadata.sourceCode,
          sourceCodeUrlScheme = metadata.sourceCodeUrlScheme,
          documentation = metadata.documentation
        )
        add(packageDependency)
      }
      add(stdlibDependency)
    }
  }

  private fun PackageUri.gatherAllModules(): List<PackageAssetUri> {
    fun PackageAssetUri.gatherModulesRecursively(): List<PackageAssetUri> {
      val self = this
      return buildList {
        for (element in packageResolver.listElements(self, null)) {
          val elementAssetUri = self.resolve(element.name)
          if (element.isDirectory) {
            addAll(elementAssetUri.gatherModulesRecursively())
          } else if (element.name.endsWith(".pkl")) {
            add(elementAssetUri)
          }
        }
      }
    }
    return toPackageAssetUri("/").gatherModulesRecursively()
  }

  private val evaluator: Evaluator by lazy {
    Evaluator.preconfigured()
  }

  private fun getSchemasFromPackage(packageUri: PackageUri): Pair<DocPackageInfo, List<ModuleSchema>>? {
    return try {
      val docPackageInfo = packageUri.toDocPackageInfo()
      val schemas = packageUri.gatherAllModules().mapNotNull {
        val schema = try {
          evaluator.evaluateSchema(ModuleSource.uri(it.uri))
        } catch (e: Throwable) {
          return@mapNotNull null
        }
        val moduleName = schema.moduleName
        val expectedModuleName = docPackageInfo.moduleNamePrefix + it.assetPath.toString().drop(1)
          .dropLast(4)
          .replace('/', '.')
        if (moduleName != expectedModuleName) {
          println(
            """
              Module ${it.uri} has an incorrect name. Skipping docs.
              
              Expected:
              $expectedModuleName
              
              Actual:
              $moduleName
            """.trimIndent()
          )
          return null
        }
        schema
      }
      docPackageInfo to schemas
    } catch (e: Throwable) {
      println("""
        Failed to fetch package info for $packageUri:
        
        ${e.message}
      """.trimIndent())
      null
    }
  }

  private fun PackageUri.toDocPackageInfo(): DocPackageInfo {
    val metadataAndChecksum = try {
      packageResolver.getDependencyMetadataAndComputeChecksum(this)
    } catch (e: PackageLoadError) {
      throw CliException("Failed to package metadata for $this: ${e.message}")
    }
    val metadata = metadataAndChecksum.first
    val checksum = metadataAndChecksum.second
    return DocPackageInfo(
      name = "${uri.authority}${uri.path.substringBeforeLast('@')}",
      moduleNamePrefix = "${metadata.name}.",
      version = metadata.version.toString(),
      importUri = toPackageAssetUri("/").toString(),
      uri = uri,
      authors = metadata.authors,
      issueTracker = metadata.issueTracker,
      dependencies = metadata.getPackageDependencies(),
      overview = metadata.description,
      extraAttributes = mapOf(
        "Checksum" to checksum.sha256
      ),
      sourceCode = metadata.sourceCode,
      sourceCodeUrlScheme = metadata.sourceCodeUrlScheme
    )
  }
}

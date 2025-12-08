/**
 * Copyright © 2024-2025 Apple Inc. and the Pkl project authors. All rights reserved.
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

import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  val gitRootDir = Path.of(args[0])
  val outputDir = Path.of(args[1])
  val doPublish = args[2] == "1"
  val repos = args.drop(3).map { repoStr ->
    val (owner, repo) = repoStr.split("/")
    Repo(owner, repo)
  }
  val docsGenerator = PackageDocs(gitRootDir, outputDir, repos)
  docsGenerator.generateDocs()
  if (doPublish) {
    docsGenerator.uploadDocs()
  }
  // hotfix: call `exitProcess` here to workaround something
  // causing the docs generator to hang.
  // TODO: figure out why this is hanging.
  exitProcess(0)
}

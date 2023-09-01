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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Serializable
data class Release(val name: String)

data class Repo(val owner: String, val name: String)

class GitHubClient(private val token: String) {
  private val baseUri = URI("https://api.github.com")

  @OptIn(ExperimentalSerializationApi::class)
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  @Suppress("UastIncorrectHttpHeaderInspection")
  private fun httpRequestBuilder(): HttpRequest.Builder {
    return HttpRequest.newBuilder().apply {
      header("Accept", "application/vnd.github+json")
      header("Authorization", "Bearer $token")
      header("X-GitHub-Api-Version", "2022-11-28")
    }
  }

  private val httpClient: HttpClient by lazy {
    HttpClient.newBuilder()
      .apply {
        connectTimeout(Duration.ofSeconds(60))
      }
      .build()
  }

  private inline fun <reified T>get(url: String): T {
    val request = httpRequestBuilder()
      .apply {
        GET()
        uri(baseUri.resolve(url))
      }
      .build()
    val respText = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    return json.decodeFromString(respText.body())
  }

  fun getReleases(repo: Repo): List<Release> =get("/repos/${repo.owner}/${repo.name}/releases")
}

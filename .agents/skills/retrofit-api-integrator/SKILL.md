---
name: retrofit-api-integrator
description: Guides the agent in integrating new API services, creating data transfer models using Kotlinx Serialization, declaring Retrofit interface endpoints, and handling network exceptions robustly.
---

# Retrofit API Integrator Skill

Use this skill when you need to introduce new network calls, fetch external data, parse JSON feeds, or connect to REST services in the Kellraadio project.

## Trigger Scenarios

*   The user requests: "fetch lyrics from a different website", "connect to a new search API", "check releases from GitHub", or "parse this online JSON playlist".
*   You find yourself modifying network layer files, adding interfaces with `@GET` / `@POST`, or creating `@Serializable` data classes.

## Procedural Walkthrough

### Step 1: Create Data Transfer Objects (DTOs)
1.  Define the response data classes with the `@Serializable` annotation from Kotlinx Serialization.
2.  Use `@SerialName` if the JSON key name has underscores or does not match Kotlin camelCase conventions.
    *   *Example:*
        ```kotlin
        @Serializable
        data class LyricResponse(
            @SerialName("lyrics_body") val lyricsBody: String,
            val copyright: String? = null
        )
        ```
3.  Ensure all optional keys or keys that might be missing are marked as nullable (e.g., `String? = null`) or have a default value to prevent serialization failures.

### Step 2: Declare the Retrofit Interface
1.  Define the query parameters, path variables, and headers.
    *   *Example:*
        ```kotlin
        interface CustomLyricsApi {
            @GET("v1/search")
            suspend fun getLyrics(
                @Query("q_track") trackName: String,
                @Query("q_artist") artistName: String
            ): LyricResponse
        }
        ```
    *   For streaming large files (like APK downloads in [GitHubReleaseApiService.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/app/radiorecalarm/update/GitHubReleaseApiService.kt)):
        ```kotlin
        @Streaming
        @GET
        suspend fun downloadFile(@Url fileUrl: String): ResponseBody
        ```

### Step 3: Register API Base URL
1.  Open [AppConfig.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/app/radiorecalarm/AppConfig.kt).
2.  Locate `object Api`.
3.  Add your base URL constant there (e.g., `const val LYRICS_PROVIDER_BASE_URL = "https://api.lyrics.com/"`).

### Step 4: Configure the Retrofit Instance & OkHttpClient
Initialize the API client inside your Repository (like [MusicInfoServices.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/app/radiorecalarm/MusicInfoServices.kt)):
1.  Use `OkHttpClient` to set connection timeouts and register user-agent headers:
    ```kotlin
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor(commonInterceptor) // Set user-agent header
        .build()
    ```
2.  Configure Retrofit to use Kotlinx Serialization converter factory:
    ```kotlin
    private val customApi = Retrofit.Builder()
        .baseUrl(AppConfig.Api.LYRICS_PROVIDER_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(CustomLyricsApi::class.java)
    ```

### Step 5: Wire Service to Repositories (Safe Execution)
1.  Expose API requests through repositories using Kotlin `Flow` or `suspend` functions.
2.  **CRITICAL**: Always wrap network calls inside `try-catch` blocks capturing general `Exception` or subclassing (e.g. `IOException`).
3.  Handle `CancellationException` properly so coroutine jobs can cancel normally:
    ```kotlin
    try {
        val result = customApi.getLyrics(track, artist)
        emit(result)
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        Log.e(TAG, "Network call failed", e)
    }
    ```

## Verification Checklists

- [ ] Verify serialization handles empty/null responses without crashing (`Json { ignoreUnknownKeys = true }`).
- [ ] Ensure correct User-Agent is sent in headers (`AppConfig.Api.USER_AGENT`).
- [ ] Verify that timeout and HTTP errors (such as 404) are caught gracefully and do not crash the app UI.

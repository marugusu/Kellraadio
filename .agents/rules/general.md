# Kellraadio: General Development Rules

Rules and guardrails for developing the Kellraadio Android app. Every agent modifying this repository must adhere to these rules.

## Core Architecture

*   **Package Root**: `app.radiorecalarm`
*   **Architecture Pattern**: Clean MVVM (Model-View-ViewModel) with the Repository pattern.
    *   **Views**: Jetpack Compose screens, widgets, and dialogs. Located under `ui/` package.
    *   **ViewModels**: Maintain UI state using `StateFlow` and handle user interactions. Located under `ui/` package.
    *   **UI State**: Keep state models immutable (e.g., `MainUiState`). Use `copy()` to update fields.
    *   **Repositories**: Encapsulate data fetching/caching logic (e.g., `RadioStationRepository`, `MusicInfoRepository`). Provide data streams via `Flow`.
*   **Media Playback**: Rely on AndroidX Media3 (ExoPlayer & MediaSession). Playback service runs in a foreground service (`RadioService`). Always handle `mediaPlayback` foreground type and respect the `WakeLock` to prevent the OS from killing the audio thread.
*   **Bluetooth Sync**: When dealing with external displays (e.g., Skoda/VW car multimedia systems), maintain the simulated fixed duration/meta synchronization in `RadioService.kt` to force metadata updates.
*   **In-App Updates**: Built-in updater in `app.radiorecalarm.update` (`AppUpdateManager`, `UpdateDialog`). Checks for new releases from public repository `marugusu/Kellraadio-releases`. Downloaded APKs are installed via Android `FileProvider`.

## UI & Jetpack Compose Standards

*   **Material 3**: Use Material 3 components and respect the dark color palette defined in `MainActivity.kt`.
*   **Adaptability & Responsive Layout**:
    *   Support both Portrait and Landscape orientations dynamically.
    *   Wide screens (width >= `AppConfig.UI.Layout.WIDTH_THRESHOLD_WIDE_SCREEN_DP`) must use split/navigation rail layout.
    *   Landscape heights above threshold must show full detail panel rather than bottom teaser panels.
*   **Previews**: Every Composable view/component must have a `@Preview` function to preview UI changes inside the IDE without running the app.
*   **Localization**: Never hardcode user-facing strings. Put them in `res/values/strings.xml` and `res/values-et/strings.xml` (Estonian is the primary locale; English is fallback). Access via `stringResource(id = R.string.my_string)`.
*   **Lifecycle & Permissions**: When navigating to Android Settings (e.g., unknown app install permission), use `rememberLauncherForActivityResult` combined with `LifecycleEventObserver(ON_RESUME)` so the UI state updates immediately upon returning.

## Concurrency (Coroutines & Flows)

*   **Threading**:
    *   Always use `Dispatchers.Main` for UI operations and ViewModel state updates.
    *   Always use `Dispatchers.IO` for disk operations, Room database operations, network calls, and file exports.
*   **Flow Collection**: Collect StateFlows in Composables using `collectAsState()` or `collectAsStateWithLifecycle()` to avoid resource leakage.
*   **Scope**: Use `viewModelScope` in ViewModels and `lifecycleScope` or `LaunchedEffect` for scope bindings in views/activities.

## Networking

*   **Retrofit & OkHttp**: All APIs are declared in interface services and instantiated using Retrofit.
*   **Serialization**: Use Kotlinx Serialization (`@Serializable` annotation on data classes). Ensure `Json { ignoreUnknownKeys = true }` is configured on clients to prevent crashes due to unexpected external API changes.

## Git & Repository Guardrails

*   **Repository Privacy**: The source code repository `marugusu/Kellraadio` is strictly **private**. Never attempt to make it public or expose secrets. Public artifacts (releases, APKs) are hosted exclusively in `marugusu/Kellraadio-releases`.
*   **Feature Branching**: Always create a new feature branch (e.g., `feature/<name>`) for new features and tasks. Never commit or merge directly into `master` without explicit user review and permission.
*   **Unstaged Work Protection**: The user frequently has uncommitted visual/UX experiments (notably in `MainActivity.kt` and `ui/SongInfoSheet.kt`). **NEVER** overwrite, discard (`git checkout --`, `git restore`), or blindly stage (`git add .`) these unstaged modifications. Always inspect `git diff` before staging.
*   **Release Automation**: Releases are published using `tools/publish_release.py`. Never perform manual browser release uploads.

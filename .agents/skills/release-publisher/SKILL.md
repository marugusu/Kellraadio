---
name: release-publisher
description: Guides the agent in publishing a new version of the Kellraadio Android app, bumping versionCode and versionName, building the APK, and uploading the release to GitHub Releases via tools/publish_release.py.
---

# Release Publisher Skill

Use this skill when you need to issue a new release of the Kellraadio application or when the user says "tee release", "tõsta versiooni", or asks to publish a new build for in-app updates.

## Trigger Scenarios

*   User requests: "tee uus release", "tõsta versiooni X peale ja release", "publish new version", or asks to make updates available for in-app updater.
*   A new feature has been merged into `master` and is ready for deployment.

## Architecture Context

*   **Private Core Repository**: `marugusu/Kellraadio` is strictly private. Code is committed and pushed here.
*   **Public Releases Repository**: `marugusu/Kellraadio-releases` is the dedicated public repository where GitHub Releases and downloadable APKs are published.
*   **In-App Updater Target**: The app queries `https://api.github.com/repos/marugusu/Kellraadio-releases/releases/latest` to check for updates.

## Procedural Walkthrough

### Step 1: Update Version in build.gradle.kts
Open [app/build.gradle.kts](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/build.gradle.kts) and update `defaultConfig`:
1.  Increment `versionCode` by 1 (e.g., from `4` to `5`).
2.  Set `versionName` to the desired semantic version (e.g., `"1.1.3"`).

### Step 2: Build the APK
Build the debug-signed APK using Gradle:
```bash
./gradlew assembleDebug
```
*Note: The installed phone build and the released APK are both signed with the local debug keystore, which ensures the Android package installer permits updating without signature mismatch.*

### Step 3: Run the Automated Release Publisher
Run [tools/publish_release.py](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/tools/publish_release.py) specifying the new tag:
```bash
python tools/publish_release.py v1.1.3 "Lühike muudatuste kirjeldus"
```
The script will:
1. Automatically read GitHub credentials from Windows Credential Manager.
2. Copy `app/build/outputs/apk/debug/app-debug.apk` to `Kellraadio-v1.1.3.apk`.
3. Create the release in `marugusu/Kellraadio-releases` via GitHub REST API.
4. Upload the APK asset to the release.

### Step 4: Commit and Push Version Bump
1.  Verify `git status`. Ensure only `app/build.gradle.kts` (and `tools/` if modified) is staged.
    *   **CRITICAL**: Do NOT stage or touch unstaged user modifications (such as in `MainActivity.kt` or `ui/SongInfoSheet.kt`).
2.  Commit the version bump:
    ```bash
    git commit -m "chore: bump version to 1.1.3 (versionCode 5)"
    ```
3.  Push to `origin master`:
    ```bash
    git push origin master
    ```

## Verification Checklists

- [ ] Verify that `publish_release.py` output confirms `[OK] APK edukalt üles laaditud!`.
- [ ] Verify the release is publicly visible at `https://github.com/marugusu/Kellraadio-releases/releases/tag/<tag>`.
- [ ] Check that `git status` on `master` is clean of unintended commits.

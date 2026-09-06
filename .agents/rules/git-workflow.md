# Kellraadio: Git & Repository Workflow Rules

Guardrails and protocols for Git operations and version control in the Kellraadio project.

## 1. Repository Privacy Guardrail

*   **Core Code Repository**: `marugusu/Kellraadio` is strictly **private**.
    *   Never attempt to make it public.
    *   Never commit API tokens, passwords, or personal credentials.
*   **Releases Repository**: `marugusu/Kellraadio-releases` is a separate, dedicated public repository used solely for hosting compiled APK releases and release notes.

## 2. Branching Strategy

*   **Dedicated Feature Branches**: Always work inside dedicated feature branches (e.g. `feature/<name>`) for any non-trivial task.
*   **Protection of `master`**: Never push unreviewed or unverified code directly to `master`. Always obtain explicit user permission before merging a feature branch into `master`.

## 3. Preservation of Unstaged Modifications (CRITICAL)

*   The user frequently maintains unstaged visual, layout, or UX tweaks in their working tree (notably in [MainActivity.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/app/radiorecalarm/MainActivity.kt) and [ui/SongInfoSheet.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/app/radiorecalarm/ui/SongInfoSheet.kt)).
*   **Rules for the Agent**:
    1.  **NEVER** discard changes with `git restore`, `git checkout --`, or `git reset --hard`.
    2.  **NEVER** blindly stage all changes with `git add .` or `git commit -a`.
    3.  Always check `git status` and `git diff` before staging, and stage specific files explicitly by path.
    4.  If switching branches or merging requires a clean working tree, use `git stash push -m "User UI tweaks" <files>` and immediately `git stash pop` after the operation, verifying the user's modifications are intact.

## 4. Release Protocol

*   Release builds must be published using the automated [tools/publish_release.py](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/tools/publish_release.py) script.
*   Do not upload releases manually via a web browser.

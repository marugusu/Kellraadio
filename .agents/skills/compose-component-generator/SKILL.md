---
name: compose-component-generator
description: Guides the agent in generating new Jetpack Compose components that are responsive (adaptive landscape/portrait/tablet), conform to the dark-theme Material 3 design, and include preview setups.
---

# Jetpack Compose Component Generator Skill

Use this skill when you are asked to design new screens, widgets, dialogue boxes, or modify existing layout components in the Kellraadio project.

## Trigger Scenarios

*   The user requests: "add a new button to control panel", "make a new view for X", "style this dialogue box", or "make it look modern".
*   You are editing files inside the `ui/` directory.

## Procedural Walkthrough

### Step 1: Design for State Hoisting (Unidirectional Data Flow)
To keep components reusable and testable:
1.  **Stateful Screen wrapper**: Houses the ViewModel and Flow collections.
2.  **Stateless Composable component**: Accepts data classes/primitive values and exposes lambda event hooks.
    *   *Example:*
        ```kotlin
        @Composable
        fun CustomFeatureButton(
            title: String,
            isEnabled: Boolean,
            onClick: () -> Unit,
            modifier: Modifier = Modifier
        ) {
            Button(
                onClick = onClick,
                enabled = isEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = modifier
            ) {
                Text(text = title, style = MaterialTheme.typography.labelLarge)
            }
        }
        ```

### Step 2: Use MaterialTheme Styling (No Hardcoding Colors)
Always read styling tokens from the active MaterialTheme:
*   **Colors**: Use `MaterialTheme.colorScheme.primary`, `onSurface`, `surfaceVariant`, `background`, etc. Refer to [MainActivity.kt](file:///c:/Users/margusra/AndroidStudioProjects/Kellraadio/app/src/main/java/app/radiorecalarm/MainActivity.kt) for the dark color palette specifications.
*   **Typography**: Use `MaterialTheme.typography.titleMedium`, `bodyMedium`, etc.
*   **Shapes**: Use `RoundedCornerShape(8.dp)` or `12.dp` matching the Braun/Hi-Fi minimal aesthetic.

### Step 3: Implement Responsive & Adaptive Layouts
Ensure the UI adapts correctly on landscape, portrait, and larger screens:
1.  Check configuration:
    ```kotlin
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
    val screenWidth = config.screenWidthDp
    ```
2.  Use standard weight-based column scaling. For instance, on wide screens (>= 500dp), partition layouts using row structures (`Row`) instead of stacked vertical panels (`Column`).
3.  Ensure touch targets are at least `48.dp` in size for accessibility.

### Step 4: Lifecycle & Permissions Awareness
When launching external system dialogs or Settings intents (e.g. unknown app install permission):
*   Use `rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult())`.
*   Combine with `LocalLifecycleOwner.current` and `LifecycleEventObserver(ON_RESUME)` so that state is refreshed immediately when returning to the app without requiring manual user cancellation or dialog reloading.

### Step 5: Include Compose Previews
For every newly created UI component, write a corresponding `@Preview` function at the bottom of the file:
```kotlin
@Preview(showBackground = true)
@Composable
fun CustomFeatureButtonPreview() {
    MaterialTheme {
        CustomFeatureButton(
            title = "Test Button",
            isEnabled = true,
            onClick = {}
        )
    }
}
```

## Verification Checklists

- [ ] Verify that UI runs correctly under both portrait and landscape orientation without clipping or wrapping issues.
- [ ] Ensure that colors are high contrast and legible under the dark color theme.
- [ ] Verify that no warnings/errors are raised during layout previews.

package app.radiorecalarm.ui

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.radiorecalarm.isTv
import app.radiorecalarm.ui.theme.KellraadioTheme

/**
 * Kohanduv modaal-ümbris: püstasendis telefonis kuvatakse `ModalBottomSheet`ina,
 * rõhtasendis ja TV-s piiratud laiusega raamitud `Dialog`ina.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptiveModalSheet(
    onDismiss: () -> Unit,
    dialogMaxWidth: Dp = 480.dp,
    dialogWidthFraction: Float = 0.92f,
    dialogMaxHeightFraction: Float = 0.9f,
    sheetHeightFraction: Float? = null,
    dismissible: Boolean = true,
    content: @Composable (isDialogMode: Boolean) -> Unit
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val useDialogFrame = isLandscape || isTv(context)

    if (useDialogFrame) {
        Dialog(
            onDismissRequest = { if (dismissible) onDismiss() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = dismissible,
                dismissOnClickOutside = dismissible
            )
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .widthIn(max = dialogMaxWidth)
                    .fillMaxWidth(dialogWidthFraction)
                    .heightIn(max = (configuration.screenHeightDp * dialogMaxHeightFraction).dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .imePadding()
                ) {
                    content(true)
                }
            }
        }
    } else {
        val sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
            confirmValueChange = { target ->
                dismissible || target != SheetValue.Hidden
            }
        )
        ModalBottomSheet(
            onDismissRequest = { if (dismissible) onDismiss() },
            sheetState = sheetState,
            properties = ModalBottomSheetProperties(shouldDismissOnBackPress = dismissible),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            dragHandle = {
                BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.outline)
            }
        ) {
            val sheetModifier = Modifier
                .fillMaxWidth()
                .let { mod ->
                    if (sheetHeightFraction != null) {
                        mod.fillMaxHeight(sheetHeightFraction)
                    } else {
                        mod
                    }
                }
                .navigationBarsPadding()
                .imePadding()
            Box(modifier = sheetModifier) {
                content(false)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun AdaptiveModalSheetPreview() {
    KellraadioTheme {
        Surface(
            shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Text(
                text = "AdaptiveModalSheet",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

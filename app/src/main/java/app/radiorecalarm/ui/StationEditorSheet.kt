package app.radiorecalarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.radiorecalarm.R
import app.radiorecalarm.RadioFilterItem
import app.radiorecalarm.RadioStation
import app.radiorecalarm.StationFormLogic
import app.radiorecalarm.getCachedCountryDisplayName
import app.radiorecalarm.getFlagEmoji
import app.radiorecalarm.ui.theme.KellraadioTheme

@Composable
fun StationEditorSheet(
    title: String,
    initialName: String,
    initialUrl: String,
    initialCountryCode: String,
    initialSubgroup: String,
    initialIconUrl: String = "",
    allCountries: List<RadioFilterItem>,
    allSubgroups: List<String>,
    existingStations: List<RadioStation> = emptyList(),
    editingStationId: Int? = null,
    activeStreamUrl: String = "",
    playbackState: StationPlaybackState = StationPlaybackState.IDLE,
    onDismiss: () -> Unit,
    onTest: (String, String) -> Unit,
    onStopTest: () -> Unit = {},
    onSave: (name: String, url: String, countryCode: String, subgroup: String, iconUrl: String) -> Unit
) {
    val view = LocalView.current
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    var countryCode by remember { mutableStateOf(initialCountryCode.trim().uppercase()) }
    var subgroup by remember { mutableStateOf(initialSubgroup) }
    var iconUrl by remember { mutableStateOf(initialIconUrl) }
    var urlTouched by remember { mutableStateOf(initialUrl.isNotBlank()) }
    var showCountryPicker by remember { mutableStateOf(false) }
    var subgroupMenuExpanded by remember { mutableStateOf(false) }
    var startedTestUrl by remember { mutableStateOf<String?>(null) }

    val fallbackStationName = stringResource(R.string.unknown_station_name)

    val resolvedCountries = remember(allCountries, existingStations) {
        StationFormLogic.buildCountryPickerItems(
            remoteCountries = allCountries,
            existingStations = existingStations
        )
    }

    val countryName = remember(countryCode, resolvedCountries) {
        if (countryCode.isBlank()) {
            ""
        } else {
            resolvedCountries.find { it.isoCode.equals(countryCode, ignoreCase = true) }?.name
                ?: getCachedCountryDisplayName(countryCode).ifBlank { countryCode }
        }
    }

    val sortedSubgroups = remember(allSubgroups, countryCode, existingStations) {
        StationFormLogic.sortSubgroupsForCountry(
            allSubgroups = allSubgroups,
            countryCode = countryCode,
            existingStations = existingStations
        )
    }
    val filteredSubgroups = remember(sortedSubgroups, subgroup) {
        StationFormLogic.filterSubgroups(sortedSubgroups, subgroup)
    }

    val trimmedUrl = url.trim()
    val isUrlValid = StationFormLogic.isValidStreamUrl(trimmedUrl)
    val showUrlError = urlTouched && trimmedUrl.isNotEmpty() && !isUrlValid
    val showHttpsQuickFix = showUrlError && StationFormLogic.shouldShowMissingSchemeQuickFix(trimmedUrl)
    val duplicateStation = remember(trimmedUrl, existingStations, editingStationId) {
        StationFormLogic.findDuplicateStationByUrl(
            url = trimmedUrl,
            existingStations = existingStations,
            editingStationId = editingStationId
        )
    }
    val canSave = name.isNotBlank() && isUrlValid

    val isTestingCurrentUrl = isUrlValid &&
        StationFormLogic.normalizeStreamUrlForComparison(activeStreamUrl) ==
        StationFormLogic.normalizeStreamUrlForComparison(trimmedUrl)
    val testPlaybackState = if (isTestingCurrentUrl) playbackState else StationPlaybackState.IDLE

    val handleTestClick = {
        view.performHapticTap()
        if (testPlaybackState == StationPlaybackState.PLAYING || testPlaybackState == StationPlaybackState.BUFFERING) {
            onStopTest()
            startedTestUrl = null
        } else if (isUrlValid) {
            startedTestUrl = trimmedUrl
            onTest(name.trim().ifBlank { fallbackStationName }, trimmedUrl)
        }
    }

    val handleDismiss = {
        val activeTest = startedTestUrl
        if (
            activeTest != null &&
            StationFormLogic.normalizeStreamUrlForComparison(activeStreamUrl) ==
            StationFormLogic.normalizeStreamUrlForComparison(activeTest) &&
            (playbackState == StationPlaybackState.PLAYING || playbackState == StationPlaybackState.BUFFERING)
        ) {
            onStopTest()
        }
        onDismiss()
    }

    val handleSave = {
        urlTouched = true
        if (canSave) {
            view.performHapticTap()
            onSave(name.trim(), trimmedUrl, countryCode.trim(), subgroup.trim(), iconUrl.trim())
        }
    }

    AdaptiveModalSheet(
        onDismiss = handleDismiss,
        dialogMaxWidth = 760.dp,
        dialogWidthFraction = 0.94f,
        dialogMaxHeightFraction = 0.92f
    ) { isDialogMode ->
        if (isDialogMode) {
            StationEditorLandscapeContent(
                title = title,
                name = name,
                onNameChange = { name = it },
                url = url,
                onUrlChange = { url = it },
                onUrlFocusLost = { if (url.isNotBlank()) urlTouched = true },
                showUrlError = showUrlError,
                showHttpsQuickFix = showHttpsQuickFix,
                onApplyHttpsPrefix = {
                    url = StationFormLogic.ensureHttpsPrefix(url)
                    urlTouched = true
                },
                duplicateStationName = duplicateStation?.name,
                countryCode = countryCode,
                countryName = countryName,
                onOpenCountryPicker = { showCountryPicker = true },
                subgroup = subgroup,
                onSubgroupChange = { subgroup = it },
                subgroupMenuExpanded = subgroupMenuExpanded,
                onSubgroupExpandedChange = { subgroupMenuExpanded = it },
                filteredSubgroups = filteredSubgroups,
                iconUrl = iconUrl,
                onIconUrlChange = { iconUrl = it },
                testPlaybackState = testPlaybackState,
                canTest = isUrlValid,
                canSave = canSave,
                onTestClick = handleTestClick,
                onDismiss = handleDismiss,
                onSave = handleSave
            )
        } else {
            StationEditorPortraitContent(
                title = title,
                name = name,
                onNameChange = { name = it },
                url = url,
                onUrlChange = { url = it },
                onUrlFocusLost = { if (url.isNotBlank()) urlTouched = true },
                showUrlError = showUrlError,
                showHttpsQuickFix = showHttpsQuickFix,
                onApplyHttpsPrefix = {
                    url = StationFormLogic.ensureHttpsPrefix(url)
                    urlTouched = true
                },
                duplicateStationName = duplicateStation?.name,
                countryCode = countryCode,
                countryName = countryName,
                onOpenCountryPicker = { showCountryPicker = true },
                subgroup = subgroup,
                onSubgroupChange = { subgroup = it },
                subgroupMenuExpanded = subgroupMenuExpanded,
                onSubgroupExpandedChange = { subgroupMenuExpanded = it },
                filteredSubgroups = filteredSubgroups,
                iconUrl = iconUrl,
                onIconUrlChange = { iconUrl = it },
                testPlaybackState = testPlaybackState,
                canTest = isUrlValid,
                canSave = canSave,
                onTestClick = handleTestClick,
                onDismiss = handleDismiss,
                onSave = handleSave
            )
        }
    }

    if (showCountryPicker) {
        FilterSheet(
            title = stringResource(R.string.filter_country),
            items = resolvedCountries,
            selectedIsoCode = countryCode.ifBlank { null },
            onClearSelection = if (countryCode.isNotBlank()) {
                {
                    countryCode = ""
                    showCountryPicker = false
                }
            } else {
                null
            },
            onDismiss = { showCountryPicker = false },
            onSelect = { item ->
                countryCode = item.isoCode?.uppercase().orEmpty()
                showCountryPicker = false
            }
        )
    }
}

@Composable
private fun StationPreviewTestCard(
    stationName: String,
    countryCode: String,
    countryName: String,
    subgroup: String,
    iconUrl: String,
    testPlaybackState: StationPlaybackState,
    canTest: Boolean,
    onTestClick: () -> Unit
) {
    val fallbackName = stringResource(R.string.unknown_station_name)
    val displayName = stationName.trim().ifBlank { fallbackName }
    val flag = if (countryCode.isNotBlank()) getFlagEmoji(countryCode) else ""
    val subtitleParts = buildList {
        if (countryName.isNotBlank()) {
            add(if (flag.isNotEmpty()) "$flag $countryName" else countryName)
        }
        if (subgroup.trim().isNotBlank()) {
            add(subgroup.trim())
        }
    }
    val isPlayingOrBuffering = testPlaybackState == StationPlaybackState.PLAYING ||
        testPlaybackState == StationPlaybackState.BUFFERING
    val isError = testPlaybackState == StationPlaybackState.ERROR

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = when {
                isPlayingOrBuffering -> MaterialTheme.colorScheme.primary
                isError -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.outline
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                StationLogoBadge(
                    stationName = displayName,
                    iconUrl = iconUrl,
                    size = 46.dp,
                    cornerRadius = 10.dp,
                    borderColor = if (isPlayingOrBuffering) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                    loadedBorderColor = if (isPlayingOrBuffering) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.Transparent
                    },
                    fallbackBackground = MaterialTheme.colorScheme.surface,
                    fallbackTextColor = MaterialTheme.colorScheme.primary,
                    fallbackFontSize = 12.sp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitleParts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitleParts.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onTestClick,
                    enabled = canTest || isPlayingOrBuffering,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isPlayingOrBuffering) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isPlayingOrBuffering) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                        contentColor = if (isPlayingOrBuffering) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    when (testPlaybackState) {
                        StationPlaybackState.BUFFERING -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.station_editor_test_buffering),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        StationPlaybackState.PLAYING -> {
                            Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.station_editor_test_stop),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        else -> {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.action_test),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }

            if (isError) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.station_editor_test_error),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun StationNameTextField(
    name: String,
    onNameChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onNext: () -> Unit
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text(stringResource(R.string.station_name)) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = stationFormTextFieldColors(),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            imeAction = ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onNext = { onNext() }),
        trailingIcon = if (name.isNotEmpty()) {
            {
                IconButton(onClick = { onNameChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )
}

@Composable
private fun StreamUrlTextField(
    url: String,
    onUrlChange: (String) -> Unit,
    onFocusLost: () -> Unit,
    showUrlError: Boolean,
    showHttpsQuickFix: Boolean,
    onApplyHttpsPrefix: () -> Unit,
    duplicateStationName: String?,
    focusRequester: FocusRequester,
    onNext: () -> Unit
) {
    var hadFocus by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            label = { Text(stringResource(R.string.stream_url)) },
            singleLine = true,
            isError = showUrlError,
            shape = RoundedCornerShape(8.dp),
            colors = stationFormTextFieldColors(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = { onNext() }),
            trailingIcon = if (url.isNotEmpty()) {
                {
                    IconButton(onClick = { onUrlChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear))
                    }
                }
            } else {
                null
            },
            supportingText = if (showUrlError) {
                {
                    Text(
                        text = stringResource(R.string.station_editor_url_invalid),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (focusState.isFocused) {
                        hadFocus = true
                    } else if (hadFocus) {
                        onFocusLost()
                    }
                }
        )

        if (showHttpsQuickFix) {
            TextButton(
                onClick = onApplyHttpsPrefix,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.heightIn(min = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.station_editor_add_https),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }

        if (!duplicateStationName.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.station_editor_duplicate_url, duplicateStationName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun CountryPickerCard(
    countryCode: String,
    countryName: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val flag = if (countryCode.isNotEmpty()) getFlagEmoji(countryCode) else ""
            if (flag.isNotEmpty()) {
                Text(flag, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.width(8.dp))
            } else {
                Icon(
                    Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = if (countryName.isNotEmpty()) countryName else stringResource(R.string.filter_country),
                style = MaterialTheme.typography.bodyLarge,
                color = if (countryName.isNotEmpty()) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubgroupDropdownField(
    subgroup: String,
    onSubgroupChange: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    filteredSubgroups: List<String>,
    focusRequester: FocusRequester,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    ExposedDropdownMenuBox(
        expanded = expanded && filteredSubgroups.isNotEmpty(),
        onExpandedChange = { onExpandedChange(!expanded) },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = subgroup,
            onValueChange = {
                onSubgroupChange(it)
                if (!expanded) onExpandedChange(true)
            },
            label = { Text(stringResource(R.string.station_subgroup)) },
            placeholder = {
                Text(
                    text = stringResource(R.string.station_editor_subgroup_hint),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = stationFormTextFieldColors(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            keyboardActions = KeyboardActions(onNext = {
                onExpandedChange(false)
                onNext()
            }),
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && filteredSubgroups.isNotEmpty())
            },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
                .focusRequester(focusRequester)
        )
        if (filteredSubgroups.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { onExpandedChange(false) }
            ) {
                filteredSubgroups.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSubgroupChange(option)
                            onExpandedChange(false)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun LogoUrlTextField(
    iconUrl: String,
    onIconUrlChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = iconUrl,
        onValueChange = onIconUrlChange,
        label = { Text(stringResource(R.string.station_icon_url)) },
        singleLine = true,
        shape = RoundedCornerShape(8.dp),
        colors = stationFormTextFieldColors(),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = if (iconUrl.isNotEmpty()) {
            {
                IconButton(onClick = { onIconUrlChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_clear))
                }
            }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
    )
}

@Composable
private fun stationFormTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline
)

@Composable
private fun StationEditorPortraitContent(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    onUrlFocusLost: () -> Unit,
    showUrlError: Boolean,
    showHttpsQuickFix: Boolean,
    onApplyHttpsPrefix: () -> Unit,
    duplicateStationName: String?,
    countryCode: String,
    countryName: String,
    onOpenCountryPicker: () -> Unit,
    subgroup: String,
    onSubgroupChange: (String) -> Unit,
    subgroupMenuExpanded: Boolean,
    onSubgroupExpandedChange: (Boolean) -> Unit,
    filteredSubgroups: List<String>,
    iconUrl: String,
    onIconUrlChange: (String) -> Unit,
    testPlaybackState: StationPlaybackState,
    canTest: Boolean,
    canSave: Boolean,
    onTestClick: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val nameFocus = remember { FocusRequester() }
    val urlFocus = remember { FocusRequester() }
    val subgroupFocus = remember { FocusRequester() }
    val logoFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StationPreviewTestCard(
                stationName = name,
                countryCode = countryCode,
                countryName = countryName,
                subgroup = subgroup,
                iconUrl = iconUrl,
                testPlaybackState = testPlaybackState,
                canTest = canTest,
                onTestClick = onTestClick
            )

            StationNameTextField(
                name = name,
                onNameChange = onNameChange,
                focusRequester = nameFocus,
                onNext = { urlFocus.requestFocus() }
            )

            StreamUrlTextField(
                url = url,
                onUrlChange = onUrlChange,
                onFocusLost = onUrlFocusLost,
                showUrlError = showUrlError,
                showHttpsQuickFix = showHttpsQuickFix,
                onApplyHttpsPrefix = onApplyHttpsPrefix,
                duplicateStationName = duplicateStationName,
                focusRequester = urlFocus,
                onNext = { subgroupFocus.requestFocus() }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                CountryPickerCard(
                    countryCode = countryCode,
                    countryName = countryName,
                    onClick = onOpenCountryPicker,
                    modifier = Modifier.weight(1f)
                )
                SubgroupDropdownField(
                    subgroup = subgroup,
                    onSubgroupChange = onSubgroupChange,
                    expanded = subgroupMenuExpanded,
                    onExpandedChange = onSubgroupExpandedChange,
                    filteredSubgroups = filteredSubgroups,
                    focusRequester = subgroupFocus,
                    onNext = { logoFocus.requestFocus() },
                    modifier = Modifier.weight(1f)
                )
            }

            LogoUrlTextField(
                iconUrl = iconUrl,
                onIconUrlChange = onIconUrlChange,
                focusRequester = logoFocus,
                onDone = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    if (canSave) onSave()
                }
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSave,
                enabled = canSave,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StationEditorLandscapeContent(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    onUrlFocusLost: () -> Unit,
    showUrlError: Boolean,
    showHttpsQuickFix: Boolean,
    onApplyHttpsPrefix: () -> Unit,
    duplicateStationName: String?,
    countryCode: String,
    countryName: String,
    onOpenCountryPicker: () -> Unit,
    subgroup: String,
    onSubgroupChange: (String) -> Unit,
    subgroupMenuExpanded: Boolean,
    onSubgroupExpandedChange: (Boolean) -> Unit,
    filteredSubgroups: List<String>,
    iconUrl: String,
    onIconUrlChange: (String) -> Unit,
    testPlaybackState: StationPlaybackState,
    canTest: Boolean,
    canSave: Boolean,
    onTestClick: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val nameFocus = remember { FocusRequester() }
    val urlFocus = remember { FocusRequester() }
    val subgroupFocus = remember { FocusRequester() }
    val logoFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StationPreviewTestCard(
                        stationName = name,
                        countryCode = countryCode,
                        countryName = countryName,
                        subgroup = subgroup,
                        iconUrl = iconUrl,
                        testPlaybackState = testPlaybackState,
                        canTest = canTest,
                        onTestClick = onTestClick
                    )
                    StationNameTextField(
                        name = name,
                        onNameChange = onNameChange,
                        focusRequester = nameFocus,
                        onNext = { urlFocus.requestFocus() }
                    )
                    StreamUrlTextField(
                        url = url,
                        onUrlChange = onUrlChange,
                        onFocusLost = onUrlFocusLost,
                        showUrlError = showUrlError,
                        showHttpsQuickFix = showHttpsQuickFix,
                        onApplyHttpsPrefix = onApplyHttpsPrefix,
                        duplicateStationName = duplicateStationName,
                        focusRequester = urlFocus,
                        onNext = { subgroupFocus.requestFocus() }
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CountryPickerCard(
                        countryCode = countryCode,
                        countryName = countryName,
                        onClick = onOpenCountryPicker,
                        modifier = Modifier.fillMaxWidth()
                    )
                    SubgroupDropdownField(
                        subgroup = subgroup,
                        onSubgroupChange = onSubgroupChange,
                        expanded = subgroupMenuExpanded,
                        onExpandedChange = onSubgroupExpandedChange,
                        filteredSubgroups = filteredSubgroups,
                        focusRequester = subgroupFocus,
                        onNext = { logoFocus.requestFocus() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    LogoUrlTextField(
                        iconUrl = iconUrl,
                        onIconUrlChange = onIconUrlChange,
                        focusRequester = logoFocus,
                        onDone = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (canSave) onSave()
                        }
                    )
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSave,
                enabled = canSave,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.action_save), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun StationEditorPortraitPreview() {
    KellraadioTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            StationEditorPortraitContent(
                title = "Lisa jaam käsitsi",
                name = "Rock FM",
                onNameChange = {},
                url = "stream.skymedia.ee/live/RockFM",
                onUrlChange = {},
                onUrlFocusLost = {},
                showUrlError = true,
                showHttpsQuickFix = true,
                onApplyHttpsPrefix = {},
                duplicateStationName = "Rock FM",
                countryCode = "EE",
                countryName = "Estonia",
                onOpenCountryPicker = {},
                subgroup = "Sky",
                onSubgroupChange = {},
                subgroupMenuExpanded = false,
                onSubgroupExpandedChange = {},
                filteredSubgroups = listOf("ERR", "Duo", "Sky", "TV3"),
                iconUrl = "",
                onIconUrlChange = {},
                testPlaybackState = StationPlaybackState.PLAYING,
                canTest = true,
                canSave = false,
                onTestClick = {},
                onDismiss = {},
                onSave = {}
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 720, heightDp = 420, backgroundColor = 0xFF0A0C10)
@Composable
private fun StationEditorLandscapePreview() {
    KellraadioTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            StationEditorLandscapeContent(
                title = "Muuda jaama",
                name = "Vikerraadio",
                onNameChange = {},
                url = "https://icecast.err.ee/vikerraadio.mp3",
                onUrlChange = {},
                onUrlFocusLost = {},
                showUrlError = false,
                showHttpsQuickFix = false,
                onApplyHttpsPrefix = {},
                duplicateStationName = null,
                countryCode = "EE",
                countryName = "Estonia",
                onOpenCountryPicker = {},
                subgroup = "ERR",
                onSubgroupChange = {},
                subgroupMenuExpanded = false,
                onSubgroupExpandedChange = {},
                filteredSubgroups = listOf("ERR", "Duo", "Sky", "TV3"),
                iconUrl = "",
                onIconUrlChange = {},
                testPlaybackState = StationPlaybackState.IDLE,
                canTest = true,
                canSave = true,
                onTestClick = {},
                onDismiss = {},
                onSave = {}
            )
        }
    }
}

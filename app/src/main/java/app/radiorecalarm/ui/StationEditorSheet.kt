package app.radiorecalarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.radiorecalarm.R
import app.radiorecalarm.RadioFilterItem
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
    onDismiss: () -> Unit,
    onTest: (String, String) -> Unit,
    onSave: (name: String, url: String, countryCode: String, subgroup: String, iconUrl: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    var countryCode by remember { mutableStateOf(initialCountryCode) }
    var subgroup by remember { mutableStateOf(initialSubgroup) }
    var iconUrl by remember { mutableStateOf(initialIconUrl) }
    var countryName by remember {
        mutableStateOf(allCountries.find { it.isoCode == initialCountryCode }?.name ?: "")
    }
    var showCountryPicker by remember { mutableStateOf(false) }
    var subgroupMenuExpanded by remember { mutableStateOf(false) }
    val fallbackStationName = stringResource(R.string.unknown_station_name)

    AdaptiveModalSheet(
        onDismiss = onDismiss,
        dialogMaxWidth = 500.dp,
        dialogWidthFraction = 0.92f,
        dialogMaxHeightFraction = 0.9f
    ) { isDialogMode ->
        StationEditorSheetContent(
            title = title,
            name = name,
            onNameChange = { name = it },
            url = url,
            onUrlChange = { url = it },
            subgroup = subgroup,
            onSubgroupChange = { subgroup = it },
            subgroupMenuExpanded = subgroupMenuExpanded,
            onSubgroupExpandedChange = { subgroupMenuExpanded = it },
            allSubgroups = allSubgroups,
            countryCode = countryCode,
            countryName = countryName,
            onOpenCountryPicker = { showCountryPicker = true },
            iconUrl = iconUrl,
            onIconUrlChange = { iconUrl = it },
            showCloseButton = isDialogMode,
            onDismiss = onDismiss,
            onTest = {
                val trimmedUrl = url.trim()
                if (trimmedUrl.isNotBlank()) {
                    onTest(name.trim().ifBlank { fallbackStationName }, trimmedUrl)
                }
            },
            onSave = {
                onSave(name.trim(), url.trim(), countryCode.trim(), subgroup.trim(), iconUrl.trim())
            },
            modifier = Modifier.padding(
                top = if (isDialogMode) 16.dp else 4.dp,
                bottom = 16.dp
            )
        )
    }

    if (showCountryPicker) {
        FilterSheet(
            title = stringResource(R.string.filter_country),
            items = allCountries,
            onDismiss = { showCountryPicker = false },
            onSelect = { item ->
                countryCode = item.isoCode ?: ""
                countryName = item.name
                showCountryPicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationEditorSheetContent(
    title: String,
    name: String,
    onNameChange: (String) -> Unit,
    url: String,
    onUrlChange: (String) -> Unit,
    subgroup: String,
    onSubgroupChange: (String) -> Unit,
    subgroupMenuExpanded: Boolean,
    onSubgroupExpandedChange: (Boolean) -> Unit,
    allSubgroups: List<String>,
    countryCode: String,
    countryName: String,
    onOpenCountryPicker: () -> Unit,
    iconUrl: String,
    onIconUrlChange: (String) -> Unit,
    showCloseButton: Boolean,
    onDismiss: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
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
            if (showCloseButton) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.station_name)) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                label = { Text(stringResource(R.string.stream_url)) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = subgroupMenuExpanded,
                onExpandedChange = { onSubgroupExpandedChange(!subgroupMenuExpanded) }
            ) {
                OutlinedTextField(
                    value = subgroup,
                    onValueChange = onSubgroupChange,
                    label = { Text(stringResource(R.string.station_subgroup)) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = subgroupMenuExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                if (allSubgroups.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = subgroupMenuExpanded,
                        onDismissRequest = { onSubgroupExpandedChange(false) }
                    ) {
                        allSubgroups.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option) },
                                onClick = {
                                    onSubgroupChange(option)
                                    onSubgroupExpandedChange(false)
                                }
                            )
                        }
                    }
                }
            }

            OutlinedCard(
                onClick = onOpenCountryPicker,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val flag = if (countryCode.isNotEmpty()) getFlagEmoji(countryCode) else ""
                    if (flag.isNotEmpty()) {
                        Text(flag, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.width(12.dp))
                    } else {
                        Icon(
                            Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Text(
                        text = if (countryName.isNotEmpty()) countryName else stringResource(R.string.filter_country),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (countryName.isNotEmpty()) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            }

            OutlinedTextField(
                value = iconUrl,
                onValueChange = onIconUrlChange,
                label = { Text(stringResource(R.string.station_icon_url)) },
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onTest,
                enabled = url.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_test))
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onSave,
                enabled = name.isNotBlank() && url.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_save))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun StationEditorSheetPreview() {
    KellraadioTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            StationEditorSheetContent(
                title = "Muuda jaama",
                name = "Rock FM",
                onNameChange = {},
                url = "https://stream.skymedia.ee/live/RockFM",
                onUrlChange = {},
                subgroup = "Rock",
                onSubgroupChange = {},
                subgroupMenuExpanded = false,
                onSubgroupExpandedChange = {},
                allSubgroups = listOf("Rock", "Pop", "Jutusaated"),
                countryCode = "EE",
                countryName = "Estonia",
                onOpenCountryPicker = {},
                iconUrl = "",
                onIconUrlChange = {},
                showCloseButton = false,
                onDismiss = {},
                onTest = {},
                onSave = {},
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }
    }
}

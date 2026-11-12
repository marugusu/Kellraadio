package app.radiorecalarm.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.radiorecalarm.R
import app.radiorecalarm.RadioFilterItem
import app.radiorecalarm.getFlagEmoji

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationEditorDialog(
    title: String,
    initialName: String,
    initialUrl: String,
    initialCountryCode: String,
    initialSubgroup: String,
    allCountries: List<RadioFilterItem>,
    allSubgroups: List<String>,
    onDismiss: () -> Unit,
    onTest: (String, String) -> Unit,
    onSave: (name: String, url: String, countryCode: String, subgroup: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    var countryCode by remember { mutableStateOf(initialCountryCode) }
    var subgroup by remember { mutableStateOf(initialSubgroup) }
    var countryName by remember {
        mutableStateOf(allCountries.find { it.isoCode == initialCountryCode }?.name ?: "")
    }
    var showCountryPicker by remember { mutableStateOf(false) }
    var subgroupMenuExpanded by remember { mutableStateOf(false) }
    val fallbackStationName = stringResource(R.string.unknown_station_name)

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(8.dp),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.station_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(R.string.stream_url)) },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                ExposedDropdownMenuBox(
                    expanded = subgroupMenuExpanded,
                    onExpandedChange = { subgroupMenuExpanded = !subgroupMenuExpanded }
                ) {
                    OutlinedTextField(
                        value = subgroup,
                        onValueChange = { subgroup = it },
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
                            onDismissRequest = { subgroupMenuExpanded = false }
                        ) {
                            allSubgroups.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option) },
                                    onClick = {
                                        subgroup = option
                                        subgroupMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedCard(
                    onClick = { showCountryPicker = true },
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
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name.trim(), url.trim(), countryCode.trim(), subgroup.trim()) },
                enabled = name.isNotBlank() && url.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        val trimmedUrl = url.trim()
                        if (trimmedUrl.isNotBlank()) {
                            onTest(name.trim().ifBlank { fallbackStationName }, trimmedUrl)
                        }
                    },
                    enabled = url.isNotBlank(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.action_test))
                }
                TextButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )

    if (showCountryPicker) {
        FilterDialog(
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

@Preview(showBackground = true)
@Composable
private fun StationEditorDialogPreview() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        StationEditorDialog(
            title = "Muuda jaama",
            initialName = "Rock FM",
            initialUrl = "https://stream.skymedia.ee/live/RockFM",
            initialCountryCode = "EE",
            initialSubgroup = "Rock",
            allCountries = listOf(RadioFilterItem(name = "Estonia", stationCount = 10, isoCode = "EE")),
            allSubgroups = listOf("Rock", "Pop", "Jutusaated"),
            onDismiss = {},
            onTest = { _, _ -> },
            onSave = { _, _, _, _ -> }
        )
    }
}


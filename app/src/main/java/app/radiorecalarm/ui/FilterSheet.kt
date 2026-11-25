package app.radiorecalarm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.radiorecalarm.R
import app.radiorecalarm.RadioFilterItem
import app.radiorecalarm.getFlagEmoji
import app.radiorecalarm.ui.theme.KellraadioTheme

@Composable
fun FilterSheet(
    title: String,
    items: List<RadioFilterItem>,
    onDismiss: () -> Unit,
    onSelect: (RadioFilterItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items else items.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    AdaptiveModalSheet(
        onDismiss = onDismiss,
        dialogMaxWidth = 480.dp,
        dialogWidthFraction = 0.92f,
        dialogMaxHeightFraction = 0.85f,
        sheetHeightFraction = 0.85f
    ) { isDialogMode ->
        FilterSheetContent(
            title = title,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            items = filteredItems,
            showCloseButton = isDialogMode,
            onDismiss = onDismiss,
            onSelect = onSelect,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isDialogMode) Modifier.heightIn(min = 240.dp) else Modifier.fillMaxHeight())
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = if (isDialogMode) 16.dp else 4.dp,
                    bottom = 0.dp
                )
        )
    }
}

@Composable
private fun FilterSheetContent(
    title: String,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    items: List<RadioFilterItem>,
    showCloseButton: Boolean,
    onDismiss: () -> Unit,
    onSelect: (RadioFilterItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp, start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            if (showCloseButton) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close))
                }
            }
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text(stringResource(R.string.search_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = null)
                    }
                }
            }
        )
        Spacer(modifier = Modifier.height(4.dp))
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.search_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = !showCloseButton)
            ) {
                items(items) { item ->
                    val flag = if (item.isoCode != null) getFlagEmoji(item.isoCode) else ""
                    ListItem(
                        headlineContent = {
                            Text(
                                text = item.name,
                                fontWeight = FontWeight.Normal,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        },
                        supportingContent = {
                            Text(
                                text = "${item.stationCount}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingContent = {
                            if (flag.isNotEmpty()) {
                                Text(
                                    text = flag,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(item) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0A0C10)
@Composable
private fun FilterSheetPreview() {
    KellraadioTheme {
        Surface(color = MaterialTheme.colorScheme.surface) {
            FilterSheetContent(
                title = "Riik",
                searchQuery = "",
                onSearchQueryChange = {},
                items = listOf(
                    RadioFilterItem(name = "Estonia", stationCount = 42, isoCode = "EE"),
                    RadioFilterItem(name = "Finland", stationCount = 128, isoCode = "FI")
                ),
                showCloseButton = false,
                onDismiss = {},
                onSelect = {},
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

package app.radiorecalarm.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.radiorecalarm.R
import app.radiorecalarm.RadioFilterItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    title: String,
    items: List<RadioFilterItem>,
    onDismiss: () -> Unit,
    onSelect: (RadioFilterItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredItems = remember(items, searchQuery) {
        if (searchQuery.isBlank()) items else items.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            Column(modifier = Modifier.padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 0.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(bottom = 16.dp, start = 4.dp)
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_placeholder)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null) }
                )
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    items(filteredItems) { item ->
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
}

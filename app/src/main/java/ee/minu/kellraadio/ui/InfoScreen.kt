package ee.minu.kellraadio.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun InfoScreen(
    versionName: String = "1.0",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Kellraadio",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Versioon $versionName",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(24.dp))

        InfoCard(
            title = "Kuidas äratus töötab?",
            text = "Äratus kasutab 'Exact Alarm' luba ja töötab ka siis, kui äpp on kinni. Kui internet puudub, võib äratus hilineda või mitte käivituda."
        )

        Spacer(modifier = Modifier.height(12.dp))

        InfoCard(
            title = "Bluetooth autos",
            text = "Äpp saadab autole spetsiaalseid signaale (kestus 5 min), et laulude nimed ilmuksid ka vanematel ekraanidel (nt Skoda/VW)."
        )

        Spacer(modifier = Modifier.height(12.dp))

        InfoCard(
            title = "Lemmikud",
            text = "Hoia jaama nimel pikalt sõrme peal, et lisada see lemmikute hulka."
        )
    }
}

@Composable
fun InfoCard(title: String, text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
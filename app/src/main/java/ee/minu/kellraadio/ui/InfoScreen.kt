package ee.minu.kellraadio.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun InfoScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val versionName = getAppVersionName(context)
    val isLandscape = androidx.compose.ui.platform.LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(
                    start = if (isLandscape) 8.dp else 16.dp,
                    end = 16.dp,
                    top = if (isLandscape) 4.dp else 0.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Rakendusest",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (isLandscape) 8.dp else 16.dp, end = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(text = "Versioon $versionName", style = MaterialTheme.typography.labelMedium, color = Color.Gray, modifier = Modifier.padding(bottom = 8.dp))

            InfoCard(title = "Sinu igapäevane raadiokaaslane", text = "Kellraadio on loodud pakkuma lihtsat ja mugavat viisi Eesti ja välismaiste raadiojaamade kuulamiseks.", icon = Icons.Default.Radio)
            Spacer(modifier = Modifier.height(12.dp))
            InfoCard(title = "Töökindel äratussüsteem", text = "Rakendus on optimeeritud nii, et Sinu lemmikjaam hakkaks mängima täpselt määratud ajal.", icon = Icons.Default.Alarm)
            Spacer(modifier = Modifier.height(12.dp))
            InfoCard(title = "Stabiilne taustaheli", text = "Oleme pööranud erilist tähelepanu sellele, et muusika mängiks katkematult ka siis, kui kasutad teisi äppe.", icon = Icons.Default.MusicNote)
            Spacer(modifier = Modifier.height(12.dp))
            InfoCard(title = "Nutikas kuulmisajalugu", text = "Kas kuulsid raadiost head lugu, aga ei mäleta nime? Ajaloo vaatest leiad kiiresti viimati mängitud palad.", icon = Icons.Default.History)

            Spacer(modifier = Modifier.height(32.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "Arendatud Eestis", style = MaterialTheme.typography.labelSmall, color = Color.Gray.copy(alpha = 0.5f))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun InfoCard(title: String, text: String, icon: ImageVector) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun getAppVersionName(context: Context): String {
    return try {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        packageInfo.versionName ?: "1.0"
    } catch (e: Exception) { "1.0" }
}
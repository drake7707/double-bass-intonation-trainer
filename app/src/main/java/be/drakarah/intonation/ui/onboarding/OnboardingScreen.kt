package be.drakarah.intonation.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import be.drakarah.intonation.ui.theme.Spacing

@Composable
fun OnboardingScreen(
    onStartCalibration: () -> Unit,
    onSkip: () -> Unit,
) {
    var showSkipWarning by remember { mutableStateOf(false) }

    if (showSkipWarning) {
        AlertDialog(
            onDismissRequest = { showSkipWarning = false },
            title = { Text("Skip calibration?") },
            text = {
                Text("The app works best when calibrated to your specific phone and room. Without it, pitch detection may be unreliable.\n\nYou can always find the full calibration wizard later in Settings.")
            },
            confirmButton = {
                TextButton(onClick = onSkip) {
                    Text("Skip anyway")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSkipWarning = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.SCREEN_EDGE_HORIZONTAL)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.MusicNote,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        
        Text(
            "Welcome to Bass Pitch",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        
        Text(
            "A specialized trainer for double bass intonation. To give you accurate feedback, the app needs to understand your instrument and your room.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        
        OnboardingItem(
            icon = Icons.Default.Mic,
            title = "Room calibration",
            description = "We'll measure the background noise so it doesn't interfere with your playing."
        )
        
        Spacer(Modifier.height(Spacing.ITEM_SPACING))
        
        OnboardingItem(
            icon = Icons.Default.GraphicEq,
            title = "Instrument setup",
            description = "You'll play a few notes so we can tune the detection specifically for your bass."
        )
        
        Spacer(Modifier.height(Spacing.SECTION_BREAK))
        
        Button(
            onClick = onStartCalibration,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Get Started")
        }
        
        Spacer(Modifier.height(Spacing.FINE_SPACING))
        
        OutlinedButton(
            onClick = { showSkipWarning = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip for now")
        }
        
        Spacer(Modifier.height(Spacing.SCREEN_EDGE_BOTTOM))
    }
}

@Composable
private fun OnboardingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.COMPONENT_SPACING)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.secondary
        )
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

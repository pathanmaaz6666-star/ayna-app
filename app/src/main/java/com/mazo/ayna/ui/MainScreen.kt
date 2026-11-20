package com.mazo.ayna.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazo.ayna.R

/**
 * State for the main screen. Owned by the caller (Activity / ViewModel);
 * this screen only renders it.
 */
data class MainUiState(
    val listening: Boolean = false,
    val statusText: String = "",
    val lastAnswer: String = "",
    val trialDaysLeft: Int? = null
)

/**
 * Main screen — matches the reference image:
 * circular face photo with purple ring at top, "Ayna" title,
 * "AI Voice Assistant • Listening…", animated purple waveform,
 * chips WhatsApp / Call / Reminder, big glowing mic button at bottom.
 */
@Composable
fun MainScreen(
    state: MainUiState,
    onMicClick: () -> Unit,
    onWhatsAppClick: () -> Unit,
    onCallClick: () -> Unit,
    onReminderClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onChatClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1B0B2E),
                        AynaColors.Background,
                        Color(0xFF0D0418)
                    )
                )
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar: trial banner space + settings gear.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onSettingsClick) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = AynaColors.Muted,
                    modifier = Modifier.size(26.dp)
                )
            }
        }

        if (state.trialDaysLeft != null) {
            TrialBanner(daysLeft = state.trialDaysLeft)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Chat button.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = AynaColors.Card,
                shape = RoundedCornerShape(20.dp),
                onClick = onChatClick,
                modifier = Modifier.border(
                    width = 1.dp,
                    color = AynaColors.Primary.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Chat,
                        contentDescription = null,
                        tint = AynaColors.Primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = stringResource(R.string.chat_button),
                        color = AynaColors.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Face with purple ring.
        Image(
            painter = painterResource(R.drawable.ayna_face),
            contentDescription = "Ayna",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(190.dp)
                .clip(CircleShape)
                .border(
                    width = 3.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            AynaColors.Primary,
                            AynaColors.AccentPink,
                            AynaColors.Secondary
                        )
                    ),
                    shape = CircleShape
                )
        )

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Ayna",
            color = AynaColors.Text,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = if (state.statusText.isNotBlank()) state.statusText
            else stringResource(R.string.ayna_tagline) + " • " + stringResource(R.string.listening),
            color = AynaColors.Muted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        WaveformView(listening = state.listening)

        // Ayna's last answer, when there is one.
        if (state.lastAnswer.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = AynaColors.Card,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = state.lastAnswer,
                    color = AynaColors.Text,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Quick-action chips.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ActionChip(
                icon = Icons.Filled.Chat,
                label = stringResource(R.string.chip_whatsapp),
                iconTint = AynaColors.WhatsAppGreen,
                onClick = onWhatsAppClick
            )
            ActionChip(
                icon = Icons.Filled.Call,
                label = stringResource(R.string.chip_call),
                iconTint = AynaColors.Secondary,
                onClick = onCallClick
            )
            ActionChip(
                icon = Icons.Filled.Notifications,
                label = stringResource(R.string.chip_reminder),
                iconTint = Color(0xFFF59E0B),
                onClick = onReminderClick
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        MicButton(listening = state.listening, onClick = onMicClick)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (state.listening) stringResource(R.string.listening)
            else stringResource(R.string.tap_to_talk),
            color = AynaColors.Muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Product of Mazo Company",
            color = AynaColors.Muted.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    iconTint: Color,
    onClick: () -> Unit
) {
    Surface(
        color = AynaColors.Card,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick,
        modifier = Modifier.border(
            width = 1.dp,
            color = AynaColors.Primary.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = label,
                color = AynaColors.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

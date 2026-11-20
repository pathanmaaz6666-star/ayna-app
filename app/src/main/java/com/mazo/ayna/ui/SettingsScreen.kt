package com.mazo.ayna.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazo.ayna.R

/**
 * Settings screen.
 *
 * - Gemini API key: password field; the key is entered by the user and must
 *   be stored ONLY in EncryptedSharedPreferences ("ayna_secure") by the
 *   caller — it is never written to a file, log or server by this UI.
 * - Language picker: Urdu / English / Roman Urdu.
 * - Wake word toggle ("Ayna" / "Hello Ayna").
 * - Trial status line.
 *
 * There is deliberately NO admin entry anywhere in this screen (or anywhere
 * in the app UI): no admin button, link, or hidden menu.
 */
@Composable
fun SettingsScreen(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    language: String,
    onLanguageChange: (String) -> Unit,
    wakeWordEnabled: Boolean,
    onWakeWordChange: (Boolean) -> Unit,
    trialDaysLeft: Int?,
    onBackClick: () -> Unit = {}
) {
    var keyVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AynaColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Wapas",
                    tint = AynaColors.Text
                )
            }
            Text(
                text = stringResource(R.string.settings_title),
                color = AynaColors.Text,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Trial status.
        Text(
            text = if (trialDaysLeft != null) {
                stringResource(R.string.pay_trial_days_left, trialDaysLeft)
            } else {
                stringResource(R.string.pay_unlocked)
            },
            color = AynaColors.Primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(AynaColors.Card)
                .padding(16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Gemini API key.
        Text(
            text = stringResource(R.string.settings_gemini_key),
            color = AynaColors.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            placeholder = { Text("AIza…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = if (keyVisible) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                    Icon(
                        imageVector = if (keyVisible) Icons.Filled.VisibilityOff
                        else Icons.Filled.Visibility,
                        contentDescription = if (keyVisible) "Key chhupayein" else "Key dikhayein",
                        tint = AynaColors.Muted
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AynaColors.Text,
                unfocusedTextColor = AynaColors.Text,
                focusedBorderColor = AynaColors.Primary,
                unfocusedBorderColor = AynaColors.Card
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_gemini_hint),
            color = AynaColors.Muted,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Language picker.
        Text(
            text = stringResource(R.string.settings_language),
            color = AynaColors.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AynaColors.Card)
        ) {
            LanguageOption(
                label = "Urdu",
                selected = language == "Urdu",
                onClick = { onLanguageChange("Urdu") }
            )
            LanguageOption(
                label = "English",
                selected = language == "English",
                onClick = { onLanguageChange("English") }
            )
            LanguageOption(
                label = "Roman Urdu",
                selected = language == "Roman Urdu",
                onClick = { onLanguageChange("Roman Urdu") }
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Wake word toggle.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(AynaColors.Card)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_wake_word),
                    color = AynaColors.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "\"Ayna\" ya \"Hello Ayna\" bol kar bulayein",
                    color = AynaColors.Muted,
                    fontSize = 13.sp
                )
            }
            Switch(
                checked = wakeWordEnabled,
                onCheckedChange = onWakeWordChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AynaColors.Text,
                    checkedTrackColor = AynaColors.Primary
                )
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        Text(
            text = "Product of Mazo Company",
            color = AynaColors.Muted.copy(alpha = 0.7f),
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AynaColors.Text,
            fontSize = 15.sp,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = AynaColors.Primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

package com.mazo.ayna.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.with
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazo.ayna.R
import kotlinx.coroutines.delay

/**
 * One-time intro flow, shown only on first launch after install.
 *
 * 6 phases:
 * 0. Branding — full screen with the face, "Made by Maaz Arif" +
 *    "Product of Mazo Company", nothing spoken, a thin progress bar filling
 *    over exactly 2 minutes (120_000 ms, timer-driven), then auto-advance.
 *    No skip button on this phase.
 * 1. Name — Ayna asks "Hey handsome! ❤️ What should I call you?" with a
 *    text field; Continue needs a non-blank name. [onNameEntered] is called
 *    with the name — the caller persists it.
 * 2. Greeting — "Assalam-o-Alaikum, main Ayna hoon"
 * 3. Who made it — Founder Maaz Arif, Product of Mazo Company
 * 4. What it can do — WhatsApp, call accept/reject, SMS, reminders, live info
 * 5. Permissions — mic, overlay, accessibility, notifications (+ Start)
 *
 * The caller is responsible for persisting the "intro shown" flag
 * (DataStore "ayna_prefs") and for actually requesting the runtime
 * permissions — this flow only explains them, it does not request them.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingFlow(
    onFinished: () -> Unit,
    onNameEntered: (String) -> Unit = {}
) {
    var page by rememberSaveable { mutableStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var brandProgress by remember { mutableStateOf(0f) }
    val isLastPage = page == 5
    val isBranding = page == 0
    val isNamePage = page == 1

    // Branding phase: exactly 2 minutes, timer-driven, then auto-advance.
    LaunchedEffect(page) {
        if (page == 0) {
            val start = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - start
                brandProgress = (elapsed / 120_000f).coerceIn(0f, 1f)
                if (elapsed >= 120_000L) {
                    page++
                    break
                }
                delay(100)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AynaColors.Background)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            // No skip on the branding phase, none on the name phase either
            // (the name is needed), and none on the last page.
            if (!isBranding && !isNamePage && !isLastPage) {
                TextButton(onClick = onFinished) {
                    Text(
                        text = stringResource(R.string.intro_skip),
                        color = AynaColors.Muted,
                        fontSize = 15.sp
                    )
                }
            }
        }

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                (slideInHorizontally { it } + fadeIn()) with
                    (slideOutHorizontally { -it } + fadeOut())
            },
            label = "onboardingPage",
            modifier = Modifier.weight(1f)
        ) { currentPage ->
            when (currentPage) {
                0 -> BrandingPage(progress = brandProgress)
                1 -> NamePage(name = name, onNameChange = { name = it })
                2 -> GreetingPage()
                3 -> MadeByPage()
                4 -> FeaturesPage()
                else -> PermissionsPage()
            }
        }

        // Page dots — only for the classic 4 intro pages (2..5).
        if (page >= 2) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                for (i in 2..5) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = if (i == page) 24.dp else 8.dp, height = 8.dp)
                            .clip(CircleShape)
                            .background(if (i == page) AynaColors.Primary else AynaColors.Card)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Branding advances itself — no button. Name needs a non-blank name.
        if (!isBranding) {
            Button(
                onClick = {
                    when {
                        isNamePage -> {
                            onNameEntered(name.trim())
                            page++
                        }
                        isLastPage -> onFinished()
                        else -> page++
                    }
                },
                enabled = !isNamePage || name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = AynaColors.Primary),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = when {
                        isNamePage -> stringResource(R.string.intro_name_continue)
                        isLastPage -> stringResource(R.string.intro_start)
                        else -> stringResource(R.string.intro_next)
                    },
                    color = AynaColors.Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        Text(
            text = "Product of Mazo Company",
            color = AynaColors.Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun BrandingPage(progress: Float) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ayna_face),
            contentDescription = "Ayna",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(200.dp)
                .clip(CircleShape)
                .border(3.dp, AynaColors.Primary, CircleShape)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.intro_branding_made_by),
            color = AynaColors.Text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.intro_branding_company),
            color = AynaColors.Muted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(40.dp))
        LinearProgressIndicator(
            progress = progress,
            color = AynaColors.Primary,
            trackColor = AynaColors.Card,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
        )
    }
}

@Composable
private fun NamePage(name: String, onNameChange: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ayna_face),
            contentDescription = "Ayna",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(CircleShape)
                .border(3.dp, AynaColors.AccentPink, CircleShape)
        )
        Spacer(modifier = Modifier.height(28.dp))
        Text(
            text = stringResource(R.string.intro_name_title),
            color = AynaColors.Text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = {
                Text(
                    text = stringResource(R.string.intro_name_hint),
                    color = AynaColors.Muted,
                    fontSize = 16.sp
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = AynaColors.Text,
                unfocusedTextColor = AynaColors.Text,
                cursorColor = AynaColors.AccentPink,
                focusedBorderColor = AynaColors.AccentPink,
                unfocusedBorderColor = AynaColors.Card,
                focusedContainerColor = AynaColors.Card,
                unfocusedContainerColor = AynaColors.Card
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun GreetingPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.drawable.ayna_face),
            contentDescription = "Ayna",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(180.dp)
                .clip(CircleShape)
                .border(3.dp, AynaColors.Primary, CircleShape)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.intro_greeting),
            color = AynaColors.Text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.intro_greeting_sub),
            color = AynaColors.Muted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MadeByPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.intro_made_by_title),
            color = AynaColors.Text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.intro_made_by_body),
            color = AynaColors.Muted,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            lineHeight = 26.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Founder: Maaz Arif",
            color = AynaColors.Primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Product of Mazo Company",
            color = AynaColors.Muted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun FeaturesPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.intro_features_title),
            color = AynaColors.Text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 34.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        FeatureRow(icon = Icons.Filled.Chat, text = "WhatsApp message bhejna — sirf bol kar")
        FeatureRow(icon = Icons.Filled.Call, text = "Call accept ya reject karna")
        FeatureRow(icon = Icons.Filled.Notifications, text = "Reminders lagana")
        FeatureRow(icon = Icons.Filled.Mic, text = "SMS bhejna aur mausam / taleem ki maloomat")
        FeatureRow(icon = Icons.Filled.Favorite, text = "Har baat — kaam, parhai, pyaar")
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Sab kuch sirf bol kar — haath lagane ki zaroorat nahi.",
            color = AynaColors.Primary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AynaColors.Card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AynaColors.Primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            color = AynaColors.Text,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun PermissionsPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.intro_permissions_title),
            color = AynaColors.Text,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.intro_permissions_body),
            color = AynaColors.Muted,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        PermissionRow(icon = Icons.Filled.Mic, title = "Microphone", detail = "Aap ki awaaz sunne ke liye")
        PermissionRow(icon = Icons.Filled.Visibility, title = "Screen ke upar dikhna", detail = "Chhota bubble har screen par")
        PermissionRow(icon = Icons.Filled.Check, title = "Accessibility", detail = "WhatsApp mein madad ke liye")
        PermissionRow(icon = Icons.Filled.Notifications, title = "Notifications", detail = "Reminders aur jawab ki khabar ke liye")
    }
}

@Composable
private fun PermissionRow(icon: ImageVector, title: String, detail: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AynaColors.Card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AynaColors.Primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column {
            Text(text = title, color = AynaColors.Text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = detail, color = AynaColors.Muted, fontSize = 13.sp)
        }
    }
}

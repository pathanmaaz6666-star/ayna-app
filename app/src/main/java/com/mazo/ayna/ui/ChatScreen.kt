package com.mazo.ayna.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazo.ayna.R

/** One chat message. role is "user" or "ai". */
data class ChatMessage(val role: String, val text: String)

/**
 * Ayna's chat UI.
 *
 * Message list with bubbles (user right / [accentColor], AI left / card),
 * text input + send button, mic button, voice-reply toggle, typing
 * indicator ("..."), auto-scroll to the latest message.
 */
@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    onSend: (String) -> Unit,
    onMicClick: () -> Unit,
    listening: Boolean,
    voiceEnabled: Boolean,
    onVoiceToggle: () -> Unit,
    title: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    typing: Boolean = false
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scroll to the latest message (or the typing indicator).
    LaunchedEffect(messages.size, typing) {
        if (messages.isNotEmpty() || typing) {
            val target = (messages.lastIndex + if (typing) 1 else 0).coerceAtLeast(0)
            runCatching { listState.animateScrollToItem(target) }
        }
    }

    val send = {
        val text = input.trim()
        if (text.isNotBlank()) {
            input = ""
            onSend(text)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AynaColors.Background)
    ) {
        // Header: title + voice-reply toggle.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = AynaColors.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onVoiceToggle) {
                Icon(
                    imageVector = if (voiceEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                    contentDescription = if (voiceEnabled) "Voice reply on" else "Voice reply off",
                    tint = if (voiceEnabled) accentColor else AynaColors.Muted,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Message list.
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message = message, accentColor = accentColor)
            }
            if (typing) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            color = AynaColors.Card,
                            shape = RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.ayna_typing),
                                color = AynaColors.Muted,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        // Input row: text field + mic + send.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.ayna_chat_hint),
                        color = AynaColors.Muted,
                        fontSize = 15.sp
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = AynaColors.Text,
                    unfocusedTextColor = AynaColors.Text,
                    cursorColor = accentColor,
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = AynaColors.Card,
                    focusedContainerColor = AynaColors.Card,
                    unfocusedContainerColor = AynaColors.Card
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = if (listening) accentColor else AynaColors.Card,
                shape = CircleShape,
                onClick = onMicClick,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.tap_to_talk),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = accentColor,
                shape = CircleShape,
                onClick = { send() },
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, accentColor: Color) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) accentColor else AynaColors.Card,
            shape = if (isUser) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
            else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Text(
                text = message.text,
                color = AynaColors.Text,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}

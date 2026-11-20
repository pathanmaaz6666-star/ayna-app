package com.mazo.ayna.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mazo.ayna.R

/**
 * Paywall state machine. The caller drives transitions:
 * SHOW -> SUBMITTING -> PENDING (server recorded, awaiting Maaz's
 * verification) -> UNLOCKED (licence active, permanent).
 */
enum class PaywallState {
    SHOW,
    SUBMITTING,
    PENDING,
    UNLOCKED
}

/**
 * Payment screen. Payment details are Maaz's exact details, taken verbatim
 * from strings.xml — never hardcode a different number, name or amount here:
 * Easypaisa 0308 9303647, account name Maaz Arif, Rs 300 one-time.
 * No card anywhere; the buyer pays in the Easypaisa app and submits the
 * transaction ID (+ optional screenshot) here.
 */
@Composable
fun PaywallScreen(
    state: PaywallState,
    onCopyNumber: () -> Unit,
    onSubmit: (txnId: String, screenshotUri: Uri?) -> Unit
) {
    var txnId by remember { mutableStateOf("") }
    var screenshotUri by remember { mutableStateOf<Uri?>(null) }

    val pickScreenshot = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        screenshotUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AynaColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.pay_title),
            color = AynaColors.Text,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 32.sp
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.pay_body),
            color = AynaColors.Muted,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 23.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Payment details card.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AynaColors.Card)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PayDetailRow(
                label = stringResource(R.string.pay_easypaisa_label),
                value = stringResource(R.string.pay_easypaisa_number)
            )
            PayDetailRow(
                label = stringResource(R.string.pay_account_name_label),
                value = stringResource(R.string.pay_account_name)
            )
            PayDetailRow(
                label = stringResource(R.string.pay_amount_label),
                value = stringResource(R.string.pay_amount)
            )

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = onCopyNumber,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Filled.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = stringResource(R.string.pay_copy_number))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Tareeqa: Easypaisa app kholein, upar wala number daal kar Rs 300 bhejein, phir neeche transaction ID likh kar submit karein.",
            color = AynaColors.Muted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        when (state) {
            PaywallState.UNLOCKED -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AynaColors.Card)
                        .padding(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = AynaColors.Success,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = stringResource(R.string.pay_unlocked),
                        color = AynaColors.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            PaywallState.PENDING -> {
                Text(
                    text = stringResource(R.string.pay_pending),
                    color = AynaColors.Text,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AynaColors.Card)
                        .padding(20.dp)
                )
            }
            PaywallState.SHOW, PaywallState.SUBMITTING -> {
                OutlinedTextField(
                    value = txnId,
                    onValueChange = { txnId = it },
                    label = { Text(stringResource(R.string.pay_txn_label)) },
                    placeholder = { Text(stringResource(R.string.pay_txn_hint)) },
                    singleLine = true,
                    enabled = state == PaywallState.SHOW,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = AynaColors.Text,
                        unfocusedTextColor = AynaColors.Text,
                        focusedLabelColor = AynaColors.Primary,
                        unfocusedLabelColor = AynaColors.Muted,
                        focusedBorderColor = AynaColors.Primary,
                        unfocusedBorderColor = AynaColors.Card
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = { pickScreenshot.launch("image/*") },
                    enabled = state == PaywallState.SHOW,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = if (screenshotUri != null) "Screenshot lag gaya ✓"
                        else stringResource(R.string.pay_attach_screenshot)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onSubmit(txnId.trim(), screenshotUri) },
                    enabled = state == PaywallState.SHOW && txnId.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AynaColors.Primary),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (state == PaywallState.SUBMITTING) {
                        CircularProgressIndicator(
                            color = AynaColors.Text,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.size(10.dp))
                    }
                    Text(
                        text = stringResource(R.string.pay_submit),
                        color = AynaColors.Text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Product of Mazo Company",
            color = AynaColors.Muted,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PayDetailRow(label: String, value: String) {
    Column {
        Text(text = label, color = AynaColors.Muted, fontSize = 13.sp)
        Text(
            text = value,
            color = AynaColors.Text,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

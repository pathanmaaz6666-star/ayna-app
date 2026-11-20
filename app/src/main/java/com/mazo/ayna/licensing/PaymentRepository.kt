package com.mazo.ayna.licensing

import android.content.Context
import android.util.Base64

/**
 * Submits the buyer's Easypaisa payment to the server and checks its status.
 *
 * Flow: buyer pays Rs 300 to Easypaisa 0308 9303647 (Maaz Arif) in the Easypaisa
 * app, then submits the Transaction ID (+ optional receipt screenshot) here.
 * The server records it as `pending_verification`; Maaz verifies it in the
 * web-only admin panel and the licence unlocks permanently.
 */
class PaymentRepository(
    private val api: AynaApi,
    private val deviceIdProvider: suspend () -> String
) {
    constructor(context: Context, api: AynaApi) : this(
        api,
        deviceIdProvider = { LicenseManager(context, api).deviceId() }
    )

    /**
     * Submit a payment. Returns success when the server recorded it as pending
     * verification; failure carries the network/server error for the UI to show.
     */
    suspend fun submitPayment(txnId: String, screenshotBytes: ByteArray?): Result<Unit> {
        val cleanTxnId = txnId.trim()
        if (cleanTxnId.isEmpty()) {
            return Result.failure(IllegalArgumentException("Transaction ID khaali hai"))
        }
        return runCatching {
            api.submitPayment(
                PaymentSubmission(
                    txnId = cleanTxnId,
                    deviceId = deviceIdProvider(),
                    screenshotBase64 = screenshotBytes?.let {
                        Base64.encodeToString(it, Base64.NO_WRAP)
                    }
                )
            )
            Unit
        }
    }

    /** Latest payment status for this device; "unknown" when offline/unreachable. */
    suspend fun paymentStatus(): PaymentStatus {
        return try {
            api.getPaymentStatus(deviceIdProvider())
        } catch (_: Exception) {
            PaymentStatus(status = "unknown")
        }
    }
}

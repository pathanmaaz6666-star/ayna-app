package com.mazo.ayna.licensing

import com.google.gson.annotations.SerializedName

/**
 * Licence states for Ayna.
 *
 * TRIAL_ACTIVE     — inside the free trial (10 days from first install by default).
 * TRIAL_EXPIRED    — trial over, paywall must be shown.
 * PAYMENT_PENDING  — buyer submitted a Transaction ID, Maaz has not verified it yet.
 * UNLOCKED         — payment verified by Maaz; permanent, one-time Rs 300.
 */
enum class LicenseStatus {
    TRIAL_ACTIVE,
    TRIAL_EXPIRED,
    PAYMENT_PENDING,
    UNLOCKED;

    companion object {
        fun fromServer(value: String?): LicenseStatus = when (value?.uppercase()) {
            "UNLOCKED" -> UNLOCKED
            "PAYMENT_PENDING" -> PAYMENT_PENDING
            "TRIAL_EXPIRED" -> TRIAL_EXPIRED
            else -> TRIAL_ACTIVE
        }
    }
}

/** Current licence state for this device. */
data class LicenseState(
    val status: LicenseStatus,
    /** Whole days of trial remaining. 0 when expired or unlocked. */
    val trialDaysLeft: Int,
    /** ISO-8601 date the trial ends (server) or null when not applicable. */
    val expiryDate: String? = null
)

/** Server-driven config. Defaults match Maaz's terms: 10 free days, Rs 300 one-time. */
data class RemoteConfig(
    @SerializedName("trial_days") val trialDays: Int = 10,
    @SerializedName("price_pkr") val pricePkr: Int = 300,
    @SerializedName("min_version") val minVersion: Int = 1,
    @SerializedName("knowledge_bundle_version") val knowledgeBundleVersion: Int = 1
)

/** What the buyer submits after paying Rs 300 via Easypaisa. */
data class PaymentSubmission(
    @SerializedName("txn_id") val txnId: String,
    @SerializedName("device_id") val deviceId: String,
    /** Optional receipt screenshot, Base64-encoded. Null when not attached. */
    @SerializedName("screenshot_b64") val screenshotBase64: String? = null
)

/** Status of the latest payment for this device (server values). */
data class PaymentStatus(
    /** "none" | "pending_verification" | "verified" | "rejected" | "unknown" (offline). */
    val status: String,
    @SerializedName("txn_id") val txnId: String? = null,
    @SerializedName("verified_at") val verifiedAt: String? = null
) {
    val isPending: Boolean get() = status == "pending_verification"
    val isVerified: Boolean get() = status == "verified"
    val isRejected: Boolean get() = status == "rejected"
    val hasPayment: Boolean get() = status != "none" && status != "unknown"
}

/** Server response for GET /api/v1/license. */
data class LicenseResponse(
    @SerializedName("device_id") val deviceId: String,
    /** "TRIAL_ACTIVE" | "TRIAL_EXPIRED" | "PAYMENT_PENDING" | "UNLOCKED" */
    val status: String,
    @SerializedName("trial_days_left") val trialDaysLeft: Int = 0,
    @SerializedName("expiry_date") val expiryDate: String? = null,
    @SerializedName("activated_at") val activatedAt: String? = null
)

/** One offline-knowledge Q&A entry inside a bundle. */
data class KnowledgeItem(
    val id: Int = 0,
    val category: String = "",
    val language: String = "",
    val keywords: List<String> = emptyList(),
    val question: String = "",
    val answer: String = "",
    val version: Int = 1
)

/** Server response for GET /api/v1/knowledge/bundle. */
data class KnowledgeBundleResponse(
    val version: Int = 1,
    val items: List<KnowledgeItem> = emptyList(),
    /** Server ids deleted by Maaz (removed from the bundle) — clients delete them too. */
    val deleted: List<String> = emptyList()
)

package com.mazo.ayna.licensing

/**
 * Payment details from Maaz — use verbatim everywhere (paywall + server records).
 * No card anywhere. Buyer pays in the Easypaisa app, then submits the Transaction ID.
 */
object PaymentConstants {
    /** Easypaisa number shown on the paywall. */
    const val EASYPAISA_NUMBER = "0308 9303647"

    /** Easypaisa account holder name shown on the paywall. */
    const val ACCOUNT_NAME = "Maaz Arif"

    /** One-time price in PKR. Permanent after payment. */
    const val PRICE_PKR = 300

    /** Free trial length in days from first install. */
    const val TRIAL_DAYS_DEFAULT = 10
}

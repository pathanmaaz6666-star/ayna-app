package com.mazo.ayna.licensing

import com.mazo.ayna.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

/**
 * Retrofit interface for the Ayna licence server.
 *
 * Base URL comes from BuildConfig.AYNA_API_URL (gradle property `aynaApiUrl`,
 * default https://api.ayna.app — placeholder until Maaz deploys the server).
 * No admin endpoints here on purpose: the buyer app must contain NO admin
 * button, screen, link or hidden entry point of any kind. Admin is web-only.
 */
interface AynaApi {

    /** Server-driven config: trial days, price, min version, knowledge bundle version. */
    @GET("api/v1/config")
    suspend fun getConfig(): RemoteConfig

    /** Licence state for this device. */
    @GET("api/v1/license")
    suspend fun getLicense(@Query("device_id") deviceId: String): LicenseResponse

    /** Submit a payment after paying Rs 300 via Easypaisa. */
    @POST("api/v1/payments")
    suspend fun submitPayment(@Body submission: PaymentSubmission): PaymentStatus

    /** Status of the latest payment for this device. */
    @GET("api/v1/payments/status")
    suspend fun getPaymentStatus(@Query("device_id") deviceId: String): PaymentStatus

    /** Offline-knowledge bundle. Pass the locally stored version; server returns the
     *  current full bundle plus its version — store it when it is newer. */
    @GET("api/v1/knowledge/bundle")
    suspend fun getKnowledgeBundle(@Query("version") version: Int): KnowledgeBundleResponse
}

object AynaApiClient {

    fun create(baseUrl: String = BuildConfig.AYNA_API_URL): AynaApi {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC logs URLs only — never headers or bodies (no txn ids / ids in logs).
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder()
            .baseUrl(baseUrl.ensureTrailingSlash())
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AynaApi::class.java)
    }

    private fun String.ensureTrailingSlash(): String =
        if (endsWith("/")) this else "$this/"
}

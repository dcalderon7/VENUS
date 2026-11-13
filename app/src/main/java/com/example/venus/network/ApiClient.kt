package com.example.venus.network

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val DEFAULT_BACKEND_URL = "https://onion-defend-adapted-until.trycloudflare.com/api/"

    fun getBaseUrl(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences("venus_secure_prefs", Context.MODE_PRIVATE)
        val savedUrl = prefs.getString("server_base_url", null)

        // Si no hay URL guardada o apunta al Worker de estaticos (workers.dev que no ejecuta Socket.IO), migrar al backend real de Express+Socket.IO
        val isInvalidWorkerUrl = savedUrl.isNullOrBlank() || savedUrl.contains("workers.dev")

        if (isInvalidWorkerUrl) {
            prefs.edit().putString("server_base_url", DEFAULT_BACKEND_URL).apply()
            return DEFAULT_BACKEND_URL
        }

        var formatted = if (!savedUrl.endsWith("/")) "$savedUrl/" else savedUrl
        if (!formatted.endsWith("api/")) {
            formatted = "${formatted}api/"
        }
        return formatted
    }

    fun setBaseUrl(context: Context, url: String) {
        val prefs = context.applicationContext.getSharedPreferences("venus_secure_prefs", Context.MODE_PRIVATE)
        var cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return

        if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
            cleanUrl = "http://$cleanUrl"
        }
        if (!cleanUrl.endsWith("/")) {
            cleanUrl = "$cleanUrl/"
        }
        if (!cleanUrl.endsWith("api/")) {
            cleanUrl = "${cleanUrl}api/"
        }
        prefs.edit().putString("server_base_url", cleanUrl).apply()
        retrofit = null
    }

    private var retrofit: Retrofit? = null

    fun getApiService(context: Context): ApiService {
        val appContext = context.applicationContext
        if (retrofit == null) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(appContext))
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(getBaseUrl(appContext))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(ApiService::class.java)
    }
}

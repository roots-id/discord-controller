package utils

import okhttp3.OkHttpClient

fun createHttpClient(apiKey: String): OkHttpClient {
    return OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("apiKey", apiKey)
                .build()
            chain.proceed(request)
        }.build()
}

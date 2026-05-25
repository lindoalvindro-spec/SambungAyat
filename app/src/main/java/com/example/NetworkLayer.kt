package com.example

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET

@JsonClass(generateAdapter = true)
data class RemoteVerse(
    val chapter_id: Int,
    val chapter_name: String,
    val verse_number: Int,
    val text_arabic: String
)

@JsonClass(generateAdapter = true)
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?
)

interface QuranApi {
    @GET("api_quran.php") 
    fun getVerses(): Call<ApiResponse<List<RemoteVerse>>>
}

object ApiClient {
    private var base_url = "http://192.168.1.12/quran_api/"
    private var retrofit: Retrofit? = null

    fun updateBaseUrl(newUrl: String) {
        val formatted = if (newUrl.trim().endsWith("/")) newUrl.trim() else "${newUrl.trim()}/"
        base_url = formatted
        retrofit = null // Force recreate on next access
    }

    val currentUrl: String
        get() = base_url

    val instance: QuranApi
        get() {
            if (retrofit == null) {
                val moshi = Moshi.Builder()
                    .addLast(KotlinJsonAdapterFactory())
                    .build()

                retrofit = Retrofit.Builder()
                    .baseUrl(base_url)
                    .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
                    .build()
            }
            return retrofit!!.create(QuranApi::class.java)
        }
}

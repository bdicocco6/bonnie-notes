package com.bonniedicocco.bonnienotes.network

import com.bonniedicocco.bonnienotes.BuildConfig
import com.google.gson.Gson
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import okhttp3.MultipartBody
import java.io.File
import java.util.concurrent.TimeUnit

data class SegmentDto(val id: Int, val speaker: String, val startSeconds: Double, val endSeconds: Double, val text: String)
data class TaskDto(val description: String, val owner: String?, val dueDate: String?, val sourceQuote: String)
data class DriveFileDto(val id: String?, val webViewLink: String?)
data class DriveResultDto(val uploaded: Boolean, val note: DriveFileDto?, val audio: DriveFileDto?)
data class ProcessResponse(
    val id: String,
    val title: String,
    val recordedAt: String,
    val transcript: String,
    val segments: List<SegmentDto>,
    val tasks: List<TaskDto>,
    val drive: DriveResultDto?
)
data class StatusResponse(val ok: Boolean, val googleDriveConfigured: Boolean, val googleDriveConnected: Boolean)
data class ConnectLinkResponse(val url: String)

interface NotesApi {
    @GET("v1/status")
    suspend fun status(): StatusResponse

    @POST("v1/google/connect-link")
    suspend fun googleConnectLink(): ConnectLinkResponse

    @Multipart
    @POST("v1/process")
    suspend fun process(
        @Part audio: MultipartBody.Part,
        @PartMap fields: Map<String, @JvmSuppressWildcards okhttp3.RequestBody>
    ): ProcessResponse
}

object ApiFactory {
    val gson = Gson()

    val api: NotesApi by lazy {
        val auth = Interceptor { chain ->
            chain.proceed(chain.request().newBuilder()
                .header("Authorization", "Bearer ${BuildConfig.APP_UPLOAD_TOKEN}")
                .build())
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(auth)
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            .connectTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(10, TimeUnit.MINUTES)
            .readTimeout(10, TimeUnit.MINUTES)
            .callTimeout(15, TimeUnit.MINUTES)
            .build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.BACKEND_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(NotesApi::class.java)
    }

    fun audioPart(file: File): MultipartBody.Part = MultipartBody.Part.createFormData(
        "audio", file.name, file.asRequestBody("audio/mp4".toMediaType())
    )

    fun textFields(values: Map<String, String>) = values.mapValues { (_, value) ->
        value.toRequestBody("text/plain".toMediaType())
    }
}

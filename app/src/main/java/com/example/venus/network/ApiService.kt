package com.example.venus.network

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.*

data class RegisterRequest(
    val name: String,
    val username: String,
    val email: String,
    val password: String
)

data class LoginRequest(
    val identifier: String,
    val password: String
)

data class ChangePasswordRequest(
    @SerializedName("currentPassword") val currentPassword: String,
    @SerializedName("newPassword") val newPassword: String,
    val confirmation: String
)

data class CompletePasswordResetRequest(
    val token: String,
    @SerializedName("newPassword") val newPassword: String,
    val confirmation: String
)

data class UserDto(
    val id: String,
    val name: String,
    val username: String,
    val email: String,
    val avatar: String?,
    val role: String?,
    @SerializedName("created_at") val createdAt: String?
)

data class AuthResponse(
    val message: String,
    val token: String,
    val user: UserDto
)

data class ProfileResponse(
    val user: UserDto
)

data class UpdateProfileRequest(
    val name: String?,
    val avatar: String?
)

data class WatchlistItemDto(
    val id: Int?,
    @SerializedName("tmdb_id") val tmdbId: String,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("created_at") val createdAt: String?
)

data class WatchlistResponse(
    val watchlist: List<WatchlistItemDto>
)

data class AddWatchlistRequest(
    @SerializedName("tmdbId") val tmdbId: String,
    @SerializedName("mediaType") val mediaType: String
)

data class HistoryItemDto(
    val id: Int?,
    @SerializedName("media_id") val mediaId: String?,
    @SerializedName("tmdb_id") val tmdbId: String,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("playback_position") val playbackPosition: Long,
    val duration: Long?,
    @SerializedName("updated_at") val updatedAt: String?
)

data class HistoryResponse(
    val history: List<HistoryItemDto>
)

data class SaveHistoryRequest(
    @SerializedName("mediaId") val mediaId: String,
    @SerializedName("tmdbId") val tmdbId: String,
    @SerializedName("mediaType") val mediaType: String,
    @SerializedName("playbackPosition") val playbackPosition: Long,
    @SerializedName("duration") val duration: Long
)

data class MediaFileDto(
    val id: String,
    @SerializedName("tmdb_id") val tmdbId: String?,
    @SerializedName("media_type") val mediaType: String,
    @SerializedName("file_name") val fileName: String,
    @SerializedName("file_size") val fileSize: Long = 0L,
    @SerializedName("file_format") val fileFormat: String,
    @SerializedName("display_title") val displayTitle: String?
)

data class MediaLibraryResponse(
    val cataloged: List<MediaFileDto>,
    val independent: List<MediaFileDto>
)

data class UpdateTitleRequest(
    @SerializedName("displayTitle") val displayTitle: String
)

data class MediaCheckResponse(
    val available: Boolean,
    val message: String?,
    val media: MediaFileDto?
)

data class MediaUploadResponse(
    val message: String,
    val media: MediaFileDto
)

interface ApiService {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @GET("auth/me")
    suspend fun getMe(): Response<ProfileResponse>

    @POST("auth/password-reset/complete")
    suspend fun completePasswordReset(@Body request: CompletePasswordResetRequest): Response<Any>

    @GET("user/profile")
    suspend fun getProfile(): Response<ProfileResponse>

    @PUT("user/profile")
    suspend fun updateProfile(@Body request: UpdateProfileRequest): Response<ProfileResponse>

    @POST("user/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Response<Any>

    @GET("user/watchlist")
    suspend fun getWatchlist(): Response<WatchlistResponse>

    @POST("user/watchlist")
    suspend fun addToWatchlist(@Body request: AddWatchlistRequest): Response<Any>

    @DELETE("user/watchlist/{id}")
    suspend fun removeFromWatchlist(@Path("id") id: String): Response<Any>

    @GET("user/history")
    suspend fun getHistory(): Response<HistoryResponse>

    @POST("user/history")
    suspend fun saveHistory(@Body request: SaveHistoryRequest): Response<Any>

    @GET("media/check/{tmdbId}")
    suspend fun checkMediaAvailability(@Path("tmdbId") tmdbId: String): Response<MediaCheckResponse>

    @GET("media/library")
    suspend fun getLibrary(): Response<MediaLibraryResponse>

    @PATCH("media/{id}/title")
    suspend fun updateMediaTitle(@Path("id") id: String, @Body request: UpdateTitleRequest): Response<Any>

    @Multipart
    @POST("media/upload")
    suspend fun uploadMedia(
        @Part file: MultipartBody.Part,
        @Part("tmdbId") tmdbId: RequestBody,
        @Part("mediaType") mediaType: RequestBody,
        @Part("displayTitle") displayTitle: RequestBody
    ): Response<MediaUploadResponse>

    @Multipart
    @POST("media/upload")
    fun uploadMediaCall(
        @Part file: MultipartBody.Part,
        @Part("tmdbId") tmdbId: RequestBody,
        @Part("mediaType") mediaType: RequestBody,
        @Part("displayTitle") displayTitle: RequestBody
    ): Call<MediaUploadResponse>

    @DELETE("media/{id}")
    suspend fun deleteMedia(@Path("id") id: String): Response<Any>
}

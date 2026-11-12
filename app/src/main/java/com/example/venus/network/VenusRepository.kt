package com.example.venus.network

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.source

class StreamingProgressRequestBody(
    private val context: Context,
    private val uri: Uri,
    private val contentType: okhttp3.MediaType?,
    private val onProgress: (Int) -> Unit
) : okhttp3.RequestBody() {
    override fun contentType() = contentType

    override fun contentLength(): Long {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
            if (sizeIndex >= 0 && cursor.moveToFirst()) {
                cursor.getLong(sizeIndex)
            } else -1L
        } ?: -1L
    }

    override fun writeTo(sink: okio.BufferedSink) {
        val totalLength = contentLength()
        var uploaded = 0L
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val source = inputStream.source()
            val buffer = okio.Buffer()
            var read: Long
            while (source.read(buffer, 2048).also { read = it } != -1L) {
                sink.write(buffer, read)
                uploaded += read
                if (totalLength > 0) {
                    val percent = ((uploaded.toDouble() / totalLength.toDouble()) * 100).toInt().coerceIn(0, 100)
                    onProgress(percent)
                }
            }
        }
    }
}

class VenusRepository(context: Context) {
    private val appContext = context.applicationContext
    private val api = ApiClient.getApiService(appContext)
    private val prefs = appContext.getSharedPreferences("venus_secure_prefs", Context.MODE_PRIVATE)

    private var activeUploadCall: retrofit2.Call<MediaUploadResponse>? = null

    fun saveToken(token: String) {
        prefs.edit().putString("jwt_token", token).apply()
    }

    fun getToken(): String? {
        return prefs.getString("jwt_token", null)
    }

    fun clearToken() {
        prefs.edit().remove("jwt_token").apply()
    }

    private val activeUploadCalls = java.util.concurrent.ConcurrentHashMap<String, retrofit2.Call<MediaUploadResponse>>()

    fun cancelUpload(uploadId: String = "default") {
        try {
            activeUploadCalls[uploadId]?.cancel()
        } catch (_: Exception) {}
        activeUploadCalls.remove(uploadId)
    }

    fun cancelUpload() {
        cancelUpload("default")
    }

    private fun parseError(e: Exception): String {
        return if (e is java.net.ConnectException || e is java.net.UnknownHostException || e is java.net.SocketTimeoutException) {
            "No se pudo conectar al servidor backend (http://10.0.2.2:5000). Verifique que Node.js y PostgreSQL estén en ejecución."
        } else {
            e.message ?: "Error de red desconocido"
        }
    }

    suspend fun register(req: RegisterRequest): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.register(req)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                saveToken(body.token)
                Result.success(body)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Error en el registro"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(parseError(e)))
        }
    }

    suspend fun login(req: LoginRequest): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.login(req)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                saveToken(body.token)
                Result.success(body)
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Credenciales incorrectas"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(parseError(e)))
        }
    }

    suspend fun getProfile(): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            val response = api.getProfile()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.user)
            } else {
                Result.failure(Exception("No se pudo obtener el perfil"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(parseError(e)))
        }
    }

    suspend fun updateProfile(name: String?, avatar: String?): Result<UserDto> = withContext(Dispatchers.IO) {
        try {
            val response = api.updateProfile(UpdateProfileRequest(name, avatar))
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.user)
            } else {
                Result.failure(Exception("No se pudo actualizar el perfil"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(parseError(e)))
        }
    }

    suspend fun getWatchlist(): Result<List<WatchlistItemDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getWatchlist()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.watchlist)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    suspend fun addToWatchlist(tmdbId: String, mediaType: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = api.addToWatchlist(AddWatchlistRequest(tmdbId, mediaType))
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun removeFromWatchlist(tmdbId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = api.removeFromWatchlist(tmdbId)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun getHistory(): Result<List<HistoryItemDto>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getHistory()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!.history)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.success(emptyList())
        }
    }

    suspend fun saveHistory(mediaId: String, tmdbId: String, mediaType: String, position: Long, duration: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = api.saveHistory(SaveHistoryRequest(mediaId, tmdbId, mediaType, position, duration))
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkMediaAvailability(tmdbId: String): MediaCheckResponse? = withContext(Dispatchers.IO) {
        try {
            val response = api.checkMediaAvailability(tmdbId)
            if (response.isSuccessful) response.body() else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun uploadMedia(uri: Uri, tmdbId: String, mediaType: String, displayTitle: String = "", uploadId: String = "default", onProgress: (Int) -> Unit): Result<MediaUploadResponse> = withContext(Dispatchers.IO) {
        try {
            var fileName = "video.mp4"
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex) ?: "video.mp4"
                }
            }

            val requestFile = StreamingProgressRequestBody(appContext, uri, "video/*".toMediaTypeOrNull(), onProgress)
            val body = MultipartBody.Part.createFormData("file", fileName, requestFile)
            val tmdbIdBody = tmdbId.toRequestBody("text/plain".toMediaTypeOrNull())
            val mediaTypeBody = mediaType.toRequestBody("text/plain".toMediaTypeOrNull())
            val displayTitleBody = displayTitle.toRequestBody("text/plain".toMediaTypeOrNull())

            val call = api.uploadMediaCall(body, tmdbIdBody, mediaTypeBody, displayTitleBody)
            activeUploadCalls[uploadId] = call

            val response = call.execute()
            activeUploadCalls.remove(uploadId)

            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                val errorStr = response.errorBody()?.string() ?: "Error al subir archivo"
                Result.failure(Exception(errorStr))
            }
        } catch (e: Exception) {
            activeUploadCalls.remove(uploadId)
            if (e is java.io.InterruptedIOException || e is java.util.concurrent.CancellationException) {
                Result.failure(Exception("Subida cancelada por el usuario"))
            } else {
                Result.failure(Exception(parseError(e)))
            }
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String, confirmation: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.changePassword(ChangePasswordRequest(currentPassword, newPassword, confirmation))
            if (response.isSuccessful) {
                Result.success("Contraseña actualizada correctamente")
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Error al cambiar la contraseña"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(parseError(e)))
        }
    }

    suspend fun completePasswordReset(token: String, newPassword: String, confirmation: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = api.completePasswordReset(CompletePasswordResetRequest(token, newPassword, confirmation))
            if (response.isSuccessful) {
                Result.success("Contraseña restablecida correctamente")
            } else {
                val errorMsg = response.errorBody()?.string() ?: "Error al restablecer la contraseña"
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Result.failure(Exception(parseError(e)))
        }
    }

    suspend fun getLibrary(): Result<MediaLibraryResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.getLibrary()
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                android.util.Log.d("VenusAudit", "VenusRepository.getLibrary() -> Cataloged: ${body.cataloged.size}, Independent: ${body.independent.size}")
                Result.success(body)
            } else {
                android.util.Log.e("VenusAudit", "VenusRepository.getLibrary() failed: code=${response.code()}, error=${response.errorBody()?.string()}")
                Result.failure(Exception("No se pudo obtener la biblioteca del servidor"))
            }
        } catch (e: Exception) {
            android.util.Log.e("VenusAudit", "VenusRepository.getLibrary() exception", e)
            Result.failure(Exception(parseError(e)))
        }
    }

    suspend fun updateMediaTitle(id: String, displayTitle: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = api.updateMediaTitle(id, UpdateTitleRequest(displayTitle))
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteMedia(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteMedia(id)
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}

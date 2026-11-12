package com.example.venus

import android.content.Context
import android.net.Uri
import java.security.MessageDigest
import java.util.UUID

data class User(
    val id: String,
    val name: String,
    val username: String,
    val email: String,
    val passwordHash: String,
    val avatar: String,
    val createdAt: Long
)

class LocalMediaStore(private val context: Context) {

    private val preferencias = context.getSharedPreferences(
        "venus_local_media",
        Context.MODE_PRIVATE
    )

    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    // --- Sistema de Usuarios y Autenticación ---
    fun obtenerUsuarioActualId(): String? {
        return preferencias.getString("active_user_id", null)
    }

    fun obtenerUsuarioActual(): User? {
        val userId = obtenerUsuarioActualId() ?: return null
        return obtenerUsuarioPorId(userId)
    }

    fun obtenerUsuarioPorId(userId: String): User? {
        val name = preferencias.getString("user_${userId}_name", null) ?: return null
        val username = preferencias.getString("user_${userId}_username", "") ?: ""
        val email = preferencias.getString("user_${userId}_email", "") ?: ""
        val passwordHash = preferencias.getString("user_${userId}_pwd", "") ?: ""
        val avatar = preferencias.getString("user_${userId}_avatar", "https://api.dicebear.com/7.x/bottts/svg?seed=$username") ?: ""
        val createdAt = preferencias.getLong("user_${userId}_created", System.currentTimeMillis())
        return User(userId, name, username, email, passwordHash, avatar, createdAt)
    }

    fun registrarUsuario(name: String, username: String, email: String, password: String): Result<User> {
        if (name.isBlank() || username.isBlank() || email.isBlank() || password.length < 6) {
            return Result.failure(IllegalArgumentException("Todos los campos son obligatorios y la contraseña debe tener al menos 6 caracteres."))
        }

        val allIds = preferencias.getStringSet("registered_user_ids", emptySet()) ?: emptySet()
        for (id in allIds) {
            val savedEmail = preferencias.getString("user_${id}_email", "")
            val savedUsername = preferencias.getString("user_${id}_username", "")
            if (savedEmail.equals(email, ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("El correo electrónico ya está registrado."))
            }
            if (savedUsername.equals(username, ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("El nombre de usuario ya está en uso."))
            }
        }

        val userId = UUID.randomUUID().toString()
        val pwdHash = hashPassword(password)
        val avatar = "https://api.dicebear.com/7.x/bottts/svg?seed=$username"
        val createdAt = System.currentTimeMillis()

        val newIds = allIds.toMutableSet()
        newIds.add(userId)

        preferencias.edit()
            .putStringSet("registered_user_ids", newIds)
            .putString("user_${userId}_name", name)
            .putString("user_${userId}_username", username)
            .putString("user_${userId}_email", email)
            .putString("user_${userId}_pwd", pwdHash)
            .putString("user_${userId}_avatar", avatar)
            .putLong("user_${userId}_created", createdAt)
            .putString("active_user_id", userId)
            .apply()

        return Result.success(User(userId, name, username, email, pwdHash, avatar, createdAt))
    }

    fun iniciarSesion(identifier: String, password: String): Result<User> {
        if (identifier.isBlank() || password.isBlank()) {
            return Result.failure(IllegalArgumentException("Ingrese su correo/usuario y contraseña."))
        }
        val allIds = preferencias.getStringSet("registered_user_ids", emptySet()) ?: emptySet()
        val pwdHash = hashPassword(password)

        for (id in allIds) {
            val email = preferencias.getString("user_${id}_email", "")
            val username = preferencias.getString("user_${id}_username", "")
            val savedPwd = preferencias.getString("user_${id}_pwd", "")

            if ((email.equals(identifier, ignoreCase = true) || username.equals(identifier, ignoreCase = true)) && savedPwd == pwdHash) {
                preferencias.edit().putString("active_user_id", id).apply()
                val user = obtenerUsuarioPorId(id)
                return if (user != null) Result.success(user) else Result.failure(IllegalStateException("Error al cargar perfil."))
            }
        }
        return Result.failure(IllegalArgumentException("Credenciales incorrectas o usuario no registrado."))
    }

    fun cerrarSesion() {
        preferencias.edit().remove("active_user_id").apply()
    }

    fun actualizarPerfil(name: String, avatar: String) {
        val userId = obtenerUsuarioActualId() ?: return
        preferencias.edit()
            .putString("user_${userId}_name", name)
            .putString("user_${userId}_avatar", avatar)
            .apply()
    }

    // --- Biblioteca y Videos Locales ---
    fun obtenerVideos(): List<String> {
        return preferencias.getStringSet("videos", emptySet())
            ?.toList()
            ?: emptyList()
    }

    fun agregarVideo(uri: Uri) {
        val videos = obtenerVideos().toMutableSet()
        videos.add(uri.toString())

        preferencias.edit()
            .putStringSet("videos", videos)
            .apply()
    }

    fun eliminarVideo(uri: String) {
        val videos = obtenerVideos().toMutableSet()
        videos.remove(uri)

        preferencias.edit()
            .putStringSet("videos", videos)
            .remove("pos_$uri")
            .remove("dur_$uri")
            .apply()
    }

    // --- Progreso de reproducción por usuario ---
    fun guardarPosicion(idOrUri: String, posicionMs: Long, duracionMs: Long) {
        val userId = obtenerUsuarioActualId() ?: "guest"
        preferencias.edit()
            .putLong("pos_${userId}_$idOrUri", posicionMs)
            .putLong("dur_${userId}_$idOrUri", duracionMs)
            .apply()
    }

    fun obtenerPosicion(idOrUri: String): Long {
        val userId = obtenerUsuarioActualId() ?: "guest"
        return preferencias.getLong("pos_${userId}_$idOrUri", 0L)
    }

    fun obtenerDuracion(idOrUri: String): Long {
        val userId = obtenerUsuarioActualId() ?: "guest"
        return preferencias.getLong("dur_${userId}_$idOrUri", 0L)
    }

    // --- Mi Lista (Independiente por usuario) ---
    fun toggleMiLista(itemId: String): Boolean {
        val userId = obtenerUsuarioActualId() ?: return false
        val key = "mi_lista_$userId"
        val miLista = preferencias.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        val nuevoEstado: Boolean
        if (miLista.contains(itemId)) {
            miLista.remove(itemId)
            nuevoEstado = false
        } else {
            miLista.add(itemId)
            nuevoEstado = true
        }
        preferencias.edit().putStringSet(key, miLista).apply()
        return nuevoEstado
    }

    fun estaEnMiLista(itemId: String): Boolean {
        val userId = obtenerUsuarioActualId() ?: return false
        val key = "mi_lista_$userId"
        return preferencias.getStringSet(key, emptySet())?.contains(itemId) ?: false
    }

    fun obtenerMiLista(): Set<String> {
        val userId = obtenerUsuarioActualId() ?: return emptySet()
        val key = "mi_lista_$userId"
        return preferencias.getStringSet(key, emptySet()) ?: emptySet()
    }

    // --- Gestor de Descargas Simulado ---
    fun toggleDescarga(idOrUri: String): Boolean {
        val descargas = obtenerDescargas().toMutableSet()
        val nuevoEstado: Boolean
        if (descargas.contains(idOrUri)) {
            descargas.remove(idOrUri)
            nuevoEstado = false
        } else {
            descargas.add(idOrUri)
            nuevoEstado = true
        }
        preferencias.edit().putStringSet("descargas", descargas).apply()
        return nuevoEstado
    }

    fun estaDescargado(idOrUri: String): Boolean {
        return obtenerDescargas().contains(idOrUri)
    }

    fun obtenerDescargas(): Set<String> {
        return preferencias.getStringSet("descargas", emptySet()) ?: emptySet()
    }

    // --- Servidor PC ---
    fun guardarServidorConectado(nombre: String, ip: String) {
        preferencias.edit()
            .putString("servidor_nombre", nombre)
            .putString("servidor_ip", ip)
            .putBoolean("servidor_conectado", true)
            .apply()
    }

    fun estaServidorConectado(): Boolean {
        return preferencias.getBoolean("servidor_conectado", false)
    }

    fun obtenerNombreServidor(): String {
        return preferencias.getString("servidor_nombre", "PC-VENUS-SERVER") ?: "PC-VENUS-SERVER"
    }

    fun obtenerIpServidor(): String {
        return preferencias.getString("servidor_ip", "192.168.1.100:8080") ?: "192.168.1.100:8080"
    }

    fun desconectarServidor() {
        preferencias.edit()
            .putBoolean("servidor_conectado", false)
            .apply()
    }
}

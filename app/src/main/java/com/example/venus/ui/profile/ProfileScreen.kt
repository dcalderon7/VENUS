package com.example.venus.ui.profile

import android.app.AlertDialog
import android.graphics.Color
import android.widget.*
import androidx.lifecycle.lifecycleScope
import com.example.venus.MainActivity
import com.example.venus.ui.components.VenusComponents
import kotlinx.coroutines.launch

@androidx.media3.common.util.UnstableApi
class ProfileScreen(private val activity: MainActivity) {

    fun mostrarPerfil() {
        activity.lifecycleScope.launch {
            val result = activity.repository.getProfile()
            val user = result.getOrNull()
            if (user == null) {
                activity.mostrarPantallaAutenticacion()
                return@launch
            }

            val pantalla = activity.prepararPantalla()

            activity.etiqueta(pantalla, "Mi Cuenta • VENUS")
            activity.titulo(pantalla, user.name, 27f)
            activity.texto(pantalla, "@${user.username} • ${user.email} • Rol: ${user.role ?: "user"}")

            activity.tarjeta(
                pantalla,
                "Cambiar Contraseña",
                "Actualiza la contraseña de tu cuenta de forma segura."
            ) {
                activity.mostrarDialogoCambiarPassword()
            }

            activity.tarjeta(
                pantalla,
                "Editar Perfil",
                "Actualiza tu nombre en el servidor."
            ) {
                val inputNombre = EditText(activity).apply {
                    setText(user.name)
                    setTextColor(Color.WHITE)
                    setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
                    background = VenusComponents.fondoRedondeado(activity.superficie2, 12, activity)
                }
                AlertDialog.Builder(activity)
                    .setTitle("Editar Perfil")
                    .setView(inputNombre)
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Guardar") { _, _ ->
                        val nuevoNombre = inputNombre.text.toString().trim()
                        if (nuevoNombre.isNotBlank()) {
                            activity.lifecycleScope.launch {
                                val updateRes = activity.repository.updateProfile(nuevoNombre, user.avatar)
                                if (updateRes.isSuccess) {
                                    Toast.makeText(activity, "Perfil actualizado", Toast.LENGTH_SHORT).show()
                                    mostrarPerfil()
                                } else {
                                    Toast.makeText(activity, updateRes.exceptionOrNull()?.message ?: "Error al actualizar", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .show()
            }

            val watchlistRes = activity.repository.getWatchlist()
            val watchlistSize = watchlistRes.getOrDefault(emptyList()).size

            activity.tarjeta(
                pantalla,
                "Mi Lista Personal",
                "$watchlistSize películas y series sincronizadas en PostgreSQL."
            ) {
                activity.mostrarMiLista()
            }

            activity.tarjeta(
                pantalla,
                "Biblioteca Local",
                "${activity.almacen.obtenerVideos().size} videos importados en este dispositivo."
            ) {
                activity.mostrarBiblioteca()
            }

            activity.tarjeta(
                pantalla,
                "Servidor PC",
                if (activity.almacen.estaServidorConectado()) "Conectado a: ${activity.almacen.obtenerNombreServidor()}" else "No conectado a computadora."
            ) {
                activity.mostrarConexion()
            }

            activity.tarjeta(
                pantalla,
                "Mi Biblioteca (Servidor)",
                "Películas del catálogo con video y videos independientes en la nube."
            ) {
                activity.mostrarMiBibliotecaIndependiente()
            }

            activity.boton(pantalla, "CERRAR SESIÓN", {
                activity.repository.clearToken()
                activity.almacen.cerrarSesion()
                Toast.makeText(activity, "Sesión cerrada correctamente", Toast.LENGTH_SHORT).show()
                activity.mostrarPantallaAutenticacion()
            }, secundario = true)
        }
    }
}

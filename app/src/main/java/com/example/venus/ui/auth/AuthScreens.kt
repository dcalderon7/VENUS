package com.example.venus.ui.auth

import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.venus.MainActivity
import com.example.venus.R
import com.example.venus.network.LoginRequest
import com.example.venus.network.RegisterRequest
import com.example.venus.ui.components.VenusComponents
import kotlinx.coroutines.launch

@androidx.media3.common.util.UnstableApi
class AuthScreens(private val activity: MainActivity) {

    fun handleDeepLinkIntent(intent: android.content.Intent?): Boolean {
        val data = intent?.data
        if (data != null && (data.path?.contains("reset-password") == true || data.host == "reset-password")) {
            val fragment = data.fragment
            val queryToken = data.getQueryParameter("token")
            val token = if (!fragment.isNullOrBlank()) {
                if (fragment.startsWith("token=")) {
                    fragment.substring(6)
                } else {
                    val uriFrag = Uri.parse("http://dummy/?$fragment")
                    uriFrag.getQueryParameter("token") ?: fragment
                }
            } else {
                queryToken
            }

            if (!token.isNullOrBlank()) {
                mostrarPantallaCompletarRecuperacion(token)
                return true
            }
        }
        return false
    }

    private fun parsearError(raw: String?): String {
        if (raw.isNullOrBlank()) return "Error en la operación"
        return try {
            val json = org.json.JSONObject(raw)
            json.optString("error", json.optString("message", raw))
        } catch (e: Exception) {
            if (raw.startsWith("{") && raw.contains("error")) {
                val parts = raw.split("\"error\":\"")
                if (parts.size > 1) {
                    parts[1].substringBefore("\"")
                } else raw
            } else {
                raw
            }
        }
    }

    fun mostrarPantallaAutenticacion() {
        activity.mostrarTopHeader(false)
        activity.mostrarBottomNav(false)
        activity.content.removeAllViews()

        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(activity.fondo)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val contenedor = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(28), activity.dp(40), activity.dp(28), activity.dp(40))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val logo = TextView(activity).apply {
            text = "V E N U S"
            textSize = 36f
            setTextColor(activity.rojo)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, activity.dp(8))
        }

        val subtitulo = TextView(activity).apply {
            text = "Cine y Series en Streaming"
            textSize = 14f
            setTextColor(activity.gris)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, activity.dp(24))
        }

        val txtError = TextView(activity).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.rgb(255, 100, 100))
            visibility = View.GONE
            setPadding(0, 0, 0, activity.dp(12))
        }

        var esRegistro = false

        val inputNombre = EditText(activity).apply {
            hint = "Nombre completo"
            setHintTextColor(activity.gris)
            setTextColor(activity.blanco)
            textSize = 14f
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = VenusComponents.fondoRedondeado(activity.superficie, 12, activity)
            isSingleLine = true
            visibility = View.GONE
        }

        val inputUserOrEmail = EditText(activity).apply {
            hint = "Correo electrónico o usuario"
            setHintTextColor(activity.gris)
            setTextColor(activity.blanco)
            textSize = 14f
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = VenusComponents.fondoRedondeado(activity.superficie, 12, activity)
            isSingleLine = true
        }

        val inputUsername = EditText(activity).apply {
            hint = "Nombre de usuario (ej: user_venus)"
            setHintTextColor(activity.gris)
            setTextColor(activity.blanco)
            textSize = 14f
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = VenusComponents.fondoRedondeado(activity.superficie, 12, activity)
            isSingleLine = true
            visibility = View.GONE
        }

        val inputPassword = EditText(activity).apply {
            hint = "Contraseña (mínimo 6 caracteres)"
            setHintTextColor(activity.gris)
            setTextColor(activity.blanco)
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = VenusComponents.fondoRedondeado(activity.superficie, 12, activity)
            isSingleLine = true
        }

        val btnAccion = TextView(activity).apply {
            text = "INICIAR SESIÓN"
            textSize = 14f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = VenusComponents.fondoRedondeado(activity.rojo, 12, activity)
            isClickable = true
            isFocusable = true
        }

        val btnCambiarModo = TextView(activity).apply {
            text = "¿No tienes cuenta? Regístrate aquí"
            textSize = 13f
            setTextColor(activity.gris)
            gravity = Gravity.CENTER
            setPadding(0, activity.dp(16), 0, 0)
            isClickable = true
            isFocusable = true
        }

        val btnOlvidoPassword = TextView(activity).apply {
            text = "¿Olvidaste tu contraseña?"
            textSize = 13f
            setTextColor(activity.gris)
            gravity = Gravity.CENTER
            setPadding(0, activity.dp(12), 0, 0)
            isClickable = true
            isFocusable = true
        }

        btnCambiarModo.setOnClickListener {
            esRegistro = !esRegistro
            if (esRegistro) {
                inputNombre.visibility = View.VISIBLE
                inputUsername.visibility = View.VISIBLE
                inputUserOrEmail.hint = "Correo electrónico"
                btnAccion.text = "REGISTRARSE"
                btnCambiarModo.text = "¿Ya tienes cuenta? Inicia sesión"
                btnOlvidoPassword.visibility = View.GONE
            } else {
                inputNombre.visibility = View.GONE
                inputUsername.visibility = View.GONE
                inputUserOrEmail.hint = "Correo electrónico o usuario"
                btnAccion.text = "INICIAR SESIÓN"
                btnCambiarModo.text = "¿No tienes cuenta? Regístrate aquí"
                btnOlvidoPassword.visibility = View.VISIBLE
            }
            txtError.visibility = View.GONE
        }

        btnOlvidoPassword.setOnClickListener {
            mostrarPantallaRecuperarPassword()
        }

        btnAccion.setOnClickListener {
            txtError.visibility = View.GONE
            val credencial = inputUserOrEmail.text.toString().trim()
            val password = inputPassword.text.toString().trim()

            if (esRegistro) {
                val nombre = inputNombre.text.toString().trim()
                val username = inputUsername.text.toString().trim()
                if (nombre.isBlank() || username.isBlank() || credencial.isBlank() || password.length < 6) {
                    txtError.text = "Todos los campos son obligatorios (contraseña >= 6 chars)"
                    txtError.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                activity.lifecycleScope.launch {
                    val res = activity.repository.register(RegisterRequest(nombre, username, credencial, password))
                    if (res.isSuccess) {
                        Toast.makeText(activity, "¡Bienvenido a VENUS, $nombre!", Toast.LENGTH_SHORT).show()
                        activity.cargarCatalogosTmdb { activity.mostrarInicio() }
                    } else {
                        txtError.text = parsearError(res.exceptionOrNull()?.message)
                        txtError.visibility = View.VISIBLE
                    }
                }
            } else {
                if (credencial.isBlank() || password.isBlank()) {
                    txtError.text = "Ingrese su correo/usuario y contraseña"
                    txtError.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                activity.lifecycleScope.launch {
                    val res = activity.repository.login(LoginRequest(credencial, password))
                    if (res.isSuccess) {
                        val data = res.getOrNull()
                        Toast.makeText(activity, "Sesión iniciada: ${data?.user?.name}", Toast.LENGTH_SHORT).show()
                        activity.cargarCatalogosTmdb { activity.mostrarInicio() }
                    } else {
                        txtError.text = parsearError(res.exceptionOrNull()?.message)
                        txtError.visibility = View.VISIBLE
                    }
                }
            }
        }

        contenedor.addView(logo)
        contenedor.addView(subtitulo)
        contenedor.addView(txtError)
        contenedor.addView(inputNombre, activity.margenAbajo(12))
        contenedor.addView(inputUsername, activity.margenAbajo(12))
        contenedor.addView(inputUserOrEmail, activity.margenAbajo(12))
        contenedor.addView(inputPassword, activity.margenAbajo(20))
        contenedor.addView(btnAccion, activity.margenAbajo(12))
        contenedor.addView(btnCambiarModo)
        contenedor.addView(btnOlvidoPassword)

        scroll.addView(contenedor)
        activity.content.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    fun mostrarPantallaRecuperarPassword() {
        activity.content.removeAllViews()
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(activity.fondo)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val contenedor = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(28), activity.dp(40), activity.dp(28), activity.dp(40))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val logo = TextView(activity).apply {
            text = "V E N U S"
            textSize = 36f
            setTextColor(activity.rojo)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, activity.dp(8))
        }

        val tituloText = TextView(activity).apply {
            text = "Recuperar Contraseña"
            textSize = 22f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, activity.dp(8))
        }

        val subtitulo = TextView(activity).apply {
            text = "Introduce tu correo electrónico para recibir las instrucciones de restablecimiento."
            textSize = 14f
            setTextColor(activity.gris)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, activity.dp(24))
        }

        val txtMensaje = TextView(activity).apply {
            text = ""
            textSize = 14f
            setTextColor(Color.rgb(0, 200, 83))
            visibility = View.GONE
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, activity.dp(16))
        }

        val inputEmail = EditText(activity).apply {
            hint = "Correo electrónico"
            setHintTextColor(activity.gris)
            setTextColor(activity.blanco)
            textSize = 14f
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = VenusComponents.fondoRedondeado(activity.superficie, 12, activity)
            isSingleLine = true
        }

        val btnEnviar = TextView(activity).apply {
            text = "ENVIAR INSTRUCCIONES"
            textSize = 14f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = VenusComponents.fondoRedondeado(activity.rojo, 12, activity)
            isClickable = true
            isFocusable = true
        }

        val btnVolver = TextView(activity).apply {
            text = "Volver al inicio de sesión"
            textSize = 13f
            setTextColor(activity.gris)
            gravity = Gravity.CENTER
            setPadding(0, activity.dp(20), 0, 0)
            isClickable = true
            isFocusable = true
        }

        btnVolver.setOnClickListener {
            mostrarPantallaAutenticacion()
        }

        btnEnviar.setOnClickListener {
            val email = inputEmail.text.toString().trim()
            if (email.isBlank()) {
                txtMensaje.text = "Ingrese un correo electrónico válido"
                txtMensaje.setTextColor(Color.rgb(255, 100, 100))
                txtMensaje.visibility = View.VISIBLE
                return@setOnClickListener
            }

            txtMensaje.text = "Si el correo está registrado, se han enviado las instrucciones de recuperación a su bandeja de entrada."
            txtMensaje.setTextColor(Color.rgb(0, 200, 83))
            txtMensaje.visibility = View.VISIBLE
            inputEmail.setText("")
        }

        contenedor.addView(logo)
        contenedor.addView(tituloText)
        contenedor.addView(subtitulo)
        contenedor.addView(txtMensaje)
        contenedor.addView(inputEmail, activity.margenAbajo(20))
        contenedor.addView(btnEnviar, activity.margenAbajo(12))
        contenedor.addView(btnVolver)

        scroll.addView(contenedor)
        activity.content.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    fun mostrarPantallaCompletarRecuperacion(token: String) {
        activity.content.removeAllViews()
        val scroll = ScrollView(activity).apply {
            isFillViewport = true
            setBackgroundColor(activity.fondo)
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        val contenedor = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(28), activity.dp(40), activity.dp(28), activity.dp(40))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val logo = TextView(activity).apply {
            text = "V E N U S"
            textSize = 36f
            setTextColor(activity.rojo)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, activity.dp(8))
        }

        val tituloText = TextView(activity).apply {
            text = "Restablecer Contraseña"
            textSize = 22f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, activity.dp(8))
        }

        val subtitulo = TextView(activity).apply {
            text = "Introduce y confirma tu nueva contraseña (mínimo 6 caracteres)."
            textSize = 14f
            setTextColor(activity.gris)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, activity.dp(24))
        }

        val txtError = TextView(activity).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.rgb(255, 100, 100))
            visibility = View.GONE
            setPadding(0, 0, 0, activity.dp(12))
        }

        val inputNewPassword = EditText(activity).apply {
            hint = "Nueva contraseña (mín. 6 caracteres)"
            setHintTextColor(activity.gris)
            setTextColor(activity.blanco)
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = VenusComponents.fondoRedondeado(activity.superficie, 12, activity)
            isSingleLine = true
        }

        val inputConfirmPassword = EditText(activity).apply {
            hint = "Confirmar nueva contraseña"
            setHintTextColor(activity.gris)
            setTextColor(activity.blanco)
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = VenusComponents.fondoRedondeado(activity.superficie, 12, activity)
            isSingleLine = true
        }

        val btnActualizar = TextView(activity).apply {
            text = "ACTUALIZAR CONTRASEÑA"
            textSize = 14f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = VenusComponents.fondoRedondeado(activity.rojo, 12, activity)
            isClickable = true
            isFocusable = true
        }

        btnActualizar.setOnClickListener {
            txtError.visibility = View.GONE
            val newPass = inputNewPassword.text.toString()
            val confirm = inputConfirmPassword.text.toString()

            if (newPass.isBlank() || confirm.isBlank()) {
                txtError.text = "Todos los campos son obligatorios"
                txtError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (newPass.length < 6) {
                txtError.text = "La contraseña debe tener al menos 6 caracteres"
                txtError.visibility = View.VISIBLE
                return@setOnClickListener
            }
            if (newPass != confirm) {
                txtError.text = "Las contraseñas no coinciden"
                txtError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            btnActualizar.isEnabled = false
            activity.lifecycleScope.launch {
                val res = activity.repository.completePasswordReset(token, newPass, confirm)
                btnActualizar.isEnabled = true
                if (res.isSuccess) {
                    Toast.makeText(activity, "Contraseña restablecida con éxito. Inicie sesión.", Toast.LENGTH_LONG).show()
                    mostrarPantallaAutenticacion()
                } else {
                    txtError.text = res.exceptionOrNull()?.message ?: "Error al restablecer la contraseña"
                    txtError.visibility = View.VISIBLE
                }
            }
        }

        contenedor.addView(logo)
        contenedor.addView(tituloText)
        contenedor.addView(subtitulo)
        contenedor.addView(txtError)
        contenedor.addView(inputNewPassword, activity.margenAbajo(12))
        contenedor.addView(inputConfirmPassword, activity.margenAbajo(20))
        contenedor.addView(btnActualizar)

        scroll.addView(contenedor)
        activity.content.addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }
}

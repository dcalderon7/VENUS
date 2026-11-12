package com.example.venus.ui.dialogs

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.Spinner
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.venus.MainActivity
import com.example.venus.R
import com.example.venus.models.PeliculaMock
import com.example.venus.network.ApiClient
import com.example.venus.ui.components.VenusComponents
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.launch

@androidx.media3.common.util.UnstableApi
object VenusDialogs {

    fun mostrarDialogoErrorConexion(activity: MainActivity, mensaje: String) {
        val bottomSheetDialog = BottomSheetDialog(activity)
        val vistaSheet = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(28), activity.dp(24), activity.dp(32))
            background = activity.fondoRedondeado(activity.superficie, 28)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val iconoCaja = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(activity.dp(72), activity.dp(72)).apply { bottomMargin = activity.dp(16) }
            background = activity.fondoRedondeado(Color.argb(40, 240, 0, 22), 36)
        }
        val icono = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(activity.dp(36), activity.dp(36)).apply { gravity = Gravity.CENTER }
            setImageResource(R.drawable.ic_wifi_off)
        }
        iconoCaja.addView(icono)
        vistaSheet.addView(iconoCaja)

        val tituloModal = TextView(activity).apply {
            text = "Sin conexión"
            textSize = 20f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, activity.dp(8))
        }
        vistaSheet.addView(tituloModal)

        val descModal = TextView(activity).apply {
            text = mensaje
            textSize = 13f
            setTextColor(activity.gris)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, activity.dp(24))
        }
        vistaSheet.addView(descModal)

        val btnReintentar = TextView(activity).apply {
            text = "Reintentar"
            textSize = 14f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(20), activity.dp(14), activity.dp(20), activity.dp(14))
            background = activity.fondoRedondeado(activity.rojo, 16)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                bottomSheetDialog.dismiss()
                activity.mostrarInicio()
            }
        }
        vistaSheet.addView(btnReintentar)

        bottomSheetDialog.setContentView(vistaSheet)
        bottomSheetDialog.show()
    }

    fun mostrarDialogoConfirmarSubida(activity: MainActivity, uri: Uri, tmdbId: String, mediaType: String) {
        var fileName = "video.mp4"
        var fileSizeStr = "Desconocido"
        try {
            activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex >= 0) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex >= 0) {
                        val sizeBytes = cursor.getLong(sizeIndex)
                        fileSizeStr = String.format(java.util.Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
                    }
                }
            }
        } catch (_: Exception) {}

        val bottomSheetDialog = BottomSheetDialog(activity)
        val vistaSheet = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(28), activity.dp(24), activity.dp(32))
            background = activity.fondoRedondeado(activity.superficie, 28)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val tituloModal = TextView(activity).apply {
            text = "Agregar Película"
            textSize = 20f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, activity.dp(16))
        }
        vistaSheet.addView(tituloModal)

        val cajaSubir = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(activity.dp(20), activity.dp(20), activity.dp(20), activity.dp(20))
            background = activity.fondoRedondeado(activity.superficie2, 16)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = activity.dp(16) }
        }

        val iconSubir = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)).apply { bottomMargin = activity.dp(12) }
            setImageResource(R.drawable.ic_cloud_upload)
        }
        val txtNombre = TextView(activity).apply {
            text = fileName
            textSize = 14f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            setPadding(0, 0, 0, activity.dp(4))
        }
        val txtTamano = TextView(activity).apply {
            text = "Tamaño: $fileSizeStr • Listo para subir"
            textSize = 12f
            setTextColor(Color.rgb(0, 200, 83))
            gravity = Gravity.CENTER
        }
        cajaSubir.addView(iconSubir)
        cajaSubir.addView(txtNombre)
        cajaSubir.addView(txtTamano)
        vistaSheet.addView(cajaSubir)

        val btnSubir = TextView(activity).apply {
            text = "Subir Película"
            textSize = 14f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(20), activity.dp(14), activity.dp(20), activity.dp(14))
            background = activity.fondoRedondeado(activity.rojo, 16)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                bottomSheetDialog.dismiss()
                activity.importarVideo(uri, tmdbId, mediaType)
            }
        }
        vistaSheet.addView(btnSubir)

        bottomSheetDialog.setContentView(vistaSheet)
        bottomSheetDialog.show()
    }

    fun mostrarDialogoExitoSubida(activity: MainActivity) {
        val bottomSheetDialog = BottomSheetDialog(activity)
        val vistaSheet = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(28), activity.dp(24), activity.dp(32))
            background = activity.fondoRedondeado(activity.superficie, 28)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val iconoCaja = FrameLayout(activity).apply {
            layoutParams = LinearLayout.LayoutParams(activity.dp(72), activity.dp(72)).apply { bottomMargin = activity.dp(16) }
            background = activity.fondoRedondeado(Color.argb(40, 0, 200, 83), 36)
        }
        val icono = ImageView(activity).apply {
            layoutParams = FrameLayout.LayoutParams(activity.dp(36), activity.dp(36)).apply { gravity = Gravity.CENTER }
            setImageResource(R.drawable.ic_play_white)
            setColorFilter(activity.verdeExito)
        }
        iconoCaja.addView(icono)
        vistaSheet.addView(iconoCaja)

        val tituloModal = TextView(activity).apply {
            text = "¡Película agregada!"
            textSize = 20f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, activity.dp(8))
        }
        vistaSheet.addView(tituloModal)

        val descModal = TextView(activity).apply {
            text = "Ahora estará disponible para todos los usuarios."
            textSize = 13f
            setTextColor(activity.gris)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, activity.dp(24))
        }
        vistaSheet.addView(descModal)

        val btnAceptar = TextView(activity).apply {
            text = "Aceptar"
            textSize = 14f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(20), activity.dp(14), activity.dp(20), activity.dp(14))
            background = activity.fondoRedondeado(activity.rojo, 16)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                bottomSheetDialog.dismiss()
                Toast.makeText(activity, "Película subida correctamente", Toast.LENGTH_SHORT).show()
            }
        }
        vistaSheet.addView(btnAceptar)

        bottomSheetDialog.setContentView(vistaSheet)
        bottomSheetDialog.show()
    }

    fun mostrarDialogoVinculacion(activity: MainActivity) {
        val inputCodigo = EditText(activity).apply {
            hint = "Ingresa el PIN de tu PC (ej: 8842)"
            setHintTextColor(activity.gris)
            setTextColor(activity.blanco)
            textSize = 14f
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = activity.fondoRedondeado(activity.superficie2, 12)
            isSingleLine = true
        }

        AlertDialog.Builder(activity)
            .setTitle("Vinculación con PC VENUS")
            .setMessage("Abre la aplicación VENUS Server en tu computadora e ingresa el código PIN mostrado en pantalla:")
            .setView(inputCodigo)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("VINCULAR") { _, _ ->
                val pin = inputCodigo.text.toString().ifBlank { "8842" }
                activity.almacen.guardarServidorConectado("PC-VENUS-SERVER", "192.168.1.105:8080 (PIN: $pin)")
                Toast.makeText(activity, "¡Servidor PC vinculado con éxito!", Toast.LENGTH_LONG).show()
                activity.mostrarConexion()
            }
            .show()
    }

    fun mostrarDialogoEstadoPelicula(activity: MainActivity, pelicula: PeliculaMock) {
        val bottomSheetDialog = BottomSheetDialog(activity)
        val vistaSheet = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(28), activity.dp(24), activity.dp(32))
            background = activity.fondoRedondeado(activity.superficie, 28)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val txtEstadoCargando = TextView(activity).apply {
            text = "Comprobando disponibilidad en servidor..."
            textSize = 13f
            setTextColor(activity.gris)
            gravity = Gravity.CENTER
            setPadding(0, activity.dp(16), 0, activity.dp(16))
        }
        vistaSheet.addView(txtEstadoCargando)

        bottomSheetDialog.setContentView(vistaSheet)
        bottomSheetDialog.show()

        activity.lifecycleScope.launch {
            val check = activity.repository.checkMediaAvailability(pelicula.id)
            val disponible = check?.available == true

            vistaSheet.removeAllViews()

            if (disponible) {
                val mediaId = check?.media?.id ?: pelicula.id
                val streamUrl = "${ApiClient.getBaseUrl(activity)}media/$mediaId/stream"
                val mediaTypeStr = if (pelicula.esSerie) "tv" else "movie"

                val iconoCaja = FrameLayout(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(activity.dp(72), activity.dp(72)).apply { bottomMargin = activity.dp(16) }
                    background = activity.fondoRedondeado(Color.argb(40, 0, 200, 83), 36)
                }
                val icono = ImageView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(activity.dp(36), activity.dp(36)).apply { gravity = Gravity.CENTER }
                    setImageResource(R.drawable.ic_play_white)
                    setColorFilter(activity.verdeExito)
                }
                iconoCaja.addView(icono)
                vistaSheet.addView(iconoCaja)

                val tituloModal = TextView(activity).apply {
                    text = "Película disponible"
                    textSize = 20f
                    setTextColor(activity.blanco)
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, activity.dp(8))
                }
                vistaSheet.addView(tituloModal)

                val descModal = TextView(activity).apply {
                    text = "Puedes reproducirla ahora desde tu servidor."
                    textSize = 13f
                    setTextColor(activity.gris)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, activity.dp(24))
                }
                vistaSheet.addView(descModal)

                val btnPlay = TextView(activity).apply {
                    text = "▶  Reproducir"
                    textSize = 14f
                    setTextColor(activity.blanco)
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(activity.dp(20), activity.dp(14), activity.dp(20), activity.dp(14))
                    background = activity.fondoRedondeado(activity.rojo, 16)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = activity.dp(10) }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        bottomSheetDialog.dismiss()
                        activity.reproducirUrlServidor(mediaId, streamUrl, pelicula.titulo, pelicula.id, mediaTypeStr)
                    }
                }
                vistaSheet.addView(btnPlay)
            } else {
                val iconContainer = FrameLayout(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(activity.dp(80), activity.dp(80)).apply { bottomMargin = activity.dp(16) }
                }
                val imgClapper = ImageView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(activity.dp(64), activity.dp(64)).apply { gravity = Gravity.CENTER }
                    setImageResource(R.drawable.ic_film_reel)
                    setColorFilter(Color.rgb(180, 180, 180))
                }
                val badgeWarning = FrameLayout(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(activity.dp(28), activity.dp(28)).apply {
                        gravity = Gravity.BOTTOM or Gravity.END
                    }
                    background = activity.fondoRedondeado(activity.rojo, 14)
                }
                val txtExclamacion = TextView(activity).apply {
                    text = "!"
                    textSize = 16f
                    setTextColor(Color.WHITE)
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                }
                badgeWarning.addView(txtExclamacion)
                iconContainer.addView(imgClapper)
                iconContainer.addView(badgeWarning)
                vistaSheet.addView(iconContainer)

                val tituloModal = TextView(activity).apply {
                    text = "Esta película todavía\nno está disponible"
                    textSize = 20f
                    setTextColor(activity.blanco)
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, activity.dp(8))
                }
                vistaSheet.addView(tituloModal)

                val descModal = TextView(activity).apply {
                    text = "Puedes agregarla al servidor\npara que todos la disfruten."
                    textSize = 13f
                    setTextColor(activity.gris)
                    gravity = Gravity.CENTER
                    setPadding(0, 0, 0, activity.dp(24))
                }
                vistaSheet.addView(descModal)

                val btnAgregar = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    setPadding(activity.dp(20), activity.dp(14), activity.dp(20), activity.dp(14))
                    background = activity.fondoRedondeado(activity.rojo, 16)
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = activity.dp(10) }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        bottomSheetDialog.dismiss()
                        activity.mostrarDialogoAgregarPelicula(pelicula.id, if (pelicula.esSerie) "tv" else "movie")
                    }
                }
                val imgCloud = ImageView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(activity.dp(20), activity.dp(20)).apply { marginEnd = activity.dp(8) }
                    setImageResource(R.drawable.ic_cloud_upload)
                    setColorFilter(Color.WHITE)
                }
                val txtBtnAgregar = TextView(activity).apply {
                    text = "Agregar Película"
                    textSize = 14f
                    setTextColor(activity.blanco)
                    setTypeface(null, Typeface.BOLD)
                }
                btnAgregar.addView(imgCloud)
                btnAgregar.addView(txtBtnAgregar)
                vistaSheet.addView(btnAgregar)

                val btnCancelar = TextView(activity).apply {
                    text = "Cancelar"
                    textSize = 14f
                    setTextColor(activity.blanco)
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    setPadding(activity.dp(20), activity.dp(14), activity.dp(20), activity.dp(14))
                    background = GradientDrawable().apply {
                        setColor(activity.superficie)
                        cornerRadius = activity.dp(16).toFloat()
                        setStroke(activity.dp(2), activity.rojo)
                    }
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { bottomSheetDialog.dismiss() }
                }
                vistaSheet.addView(btnCancelar)
            }
        }
    }

    fun mostrarDialogoAgregarPelicula(activity: MainActivity, tmdbId: String, mediaType: String) {
        activity.targetTmdbIdForUpload = tmdbId
        activity.targetMediaTypeForUpload = mediaType
        val bottomSheetDialog = BottomSheetDialog(activity)
        val vistaSheet = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(24), activity.dp(28), activity.dp(24), activity.dp(32))
            background = activity.fondoRedondeado(activity.superficie, 28)
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val topRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, activity.dp(16))
        }
        val btnBack = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(activity.dp(24), activity.dp(24)).apply { marginEnd = activity.dp(12) }
            setImageResource(R.drawable.ic_arrow_back)
            setColorFilter(activity.blanco)
            isClickable = true
            isFocusable = true
            setOnClickListener { bottomSheetDialog.dismiss() }
        }
        val tituloModal = TextView(activity).apply {
            text = "Agregar Película"
            textSize = 20f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
        }
        topRow.addView(btnBack)
        topRow.addView(tituloModal)
        vistaSheet.addView(topRow)

        val cajaSubir = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(activity.dp(24), activity.dp(24), activity.dp(24), activity.dp(24))
            background = activity.fondoRedondeado(activity.superficie2, 16)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = activity.dp(16) }
        }

        val iconSubir = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(activity.dp(48), activity.dp(48)).apply { bottomMargin = activity.dp(12) }
            setImageResource(R.drawable.ic_cloud_upload)
        }
        val txtSubir1 = TextView(activity).apply {
            text = "Sube tu película al servidor"
            textSize = 15f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, activity.dp(4))
        }
        val txtSubir2 = TextView(activity).apply {
            text = "Formatos permitidos:\nMP4, MKV, AVI, etc."
            textSize = 12f
            setTextColor(activity.gris)
            gravity = Gravity.CENTER
        }
        cajaSubir.addView(iconSubir)
        cajaSubir.addView(txtSubir1)
        cajaSubir.addView(txtSubir2)
        vistaSheet.addView(cajaSubir)

        val btnSelector = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = GradientDrawable().apply {
                setColor(activity.superficie2)
                cornerRadius = activity.dp(12).toFloat()
                setStroke(activity.dp(2), activity.rojo, activity.dp(8).toFloat(), activity.dp(4).toFloat())
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { bottomMargin = activity.dp(16) }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                bottomSheetDialog.dismiss()
                activity.selectorVideo.launch(arrayOf("video/*"))
            }
        }

        val iconFolder = ImageView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(activity.dp(20), activity.dp(20)).apply { marginEnd = activity.dp(10) }
            setImageResource(R.drawable.ic_folder)
            setColorFilter(Color.WHITE)
        }

        val txtSelector = TextView(activity).apply {
            text = "Seleccionar Archivo"
            textSize = 13f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
        }

        btnSelector.addView(iconFolder)
        btnSelector.addView(txtSelector)
        vistaSheet.addView(btnSelector)

        val btnSubir = TextView(activity).apply {
            text = "Subir Película"
            textSize = 14f
            setTextColor(activity.blanco)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(activity.dp(20), activity.dp(14), activity.dp(20), activity.dp(14))
            background = activity.fondoRedondeado(activity.rojo, 16)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                bottomSheetDialog.dismiss()
                activity.mostrarDialogoExitoSubida()
            }
        }
        vistaSheet.addView(btnSubir)

        bottomSheetDialog.setContentView(vistaSheet)
        bottomSheetDialog.show()
    }

    fun mostrarDialogoCambiarPassword(activity: MainActivity) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(16), activity.dp(20), activity.dp(8))
        }

        val txtError = TextView(activity).apply {
            text = ""
            textSize = 13f
            setTextColor(Color.rgb(255, 100, 100))
            visibility = View.GONE
            setPadding(0, 0, 0, activity.dp(10))
        }

        val inputCurrent = EditText(activity).apply {
            hint = "Contraseña actual"
            setHintTextColor(activity.gris)
            setTextColor(activity.blanco)
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = activity.fondoRedondeado(activity.superficie2, 12)
        }

        val inputNew = EditText(activity).apply {
            hint = "Nueva contraseña (mín. 6 caracteres)"
            setHintTextColor(activity.gris)
            setTextColor(activity.blanco)
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = activity.fondoRedondeado(activity.superficie2, 12)
        }

        val inputConfirm = EditText(activity).apply {
            hint = "Confirmar nueva contraseña"
            setHintTextColor(activity.gris)
            setTextColor(activity.blanco)
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = activity.fondoRedondeado(activity.superficie2, 12)
        }

        layout.addView(txtError)
        layout.addView(inputCurrent, activity.margenAbajo(12))
        layout.addView(inputNew, activity.margenAbajo(12))
        layout.addView(inputConfirm, activity.margenAbajo(8))

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Cambiar Contraseña")
            .setView(layout)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Actualizar", null)
            .create()

        dialog.setOnShowListener {
            val btnPositive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            btnPositive.setOnClickListener {
                txtError.visibility = View.GONE
                val current = inputCurrent.text.toString()
                val newPass = inputNew.text.toString()
                val confirm = inputConfirm.text.toString()

                if (current.isBlank() || newPass.isBlank() || confirm.isBlank()) {
                    txtError.text = "Todos los campos son obligatorios"
                    txtError.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                if (newPass.length < 6) {
                    txtError.text = "La nueva contraseña debe tener al menos 6 caracteres"
                    txtError.visibility = View.VISIBLE
                    return@setOnClickListener
                }
                if (newPass != confirm) {
                    txtError.text = "Las contraseñas no coinciden"
                    txtError.visibility = View.VISIBLE
                    return@setOnClickListener
                }

                btnPositive.isEnabled = false
                activity.lifecycleScope.launch {
                    val res = activity.repository.changePassword(current, newPass, confirm)
                    btnPositive.isEnabled = true
                    if (res.isSuccess) {
                        Toast.makeText(activity, "Contraseña actualizada con éxito", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    } else {
                        txtError.text = res.exceptionOrNull()?.message ?: "Error al actualizar la contraseña"
                        txtError.visibility = View.VISIBLE
                    }
                }
            }
        }
        dialog.show()
    }

    fun mostrarDialogoSubirVideoIndependiente(activity: MainActivity) {
        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(activity.dp(20), activity.dp(16), activity.dp(20), activity.dp(8))
        }

        val inputTitle = EditText(activity).apply {
            hint = "Título del video (obligatorio)"
            setHintTextColor(activity.gris)
            setTextColor(activity.blanco)
            textSize = 14f
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = activity.fondoRedondeado(activity.superficie2, 12)
        }

        val inputDesc = EditText(activity).apply {
            hint = "Descripción (opcional)"
            setHintTextColor(activity.gris)
            setTextColor(activity.blanco)
            textSize = 14f
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = activity.fondoRedondeado(activity.superficie2, 12)
        }

        val categories = arrayOf("Documental", "Personal", "Cortometraje", "Otro")
        val spinnerCategory = Spinner(activity).apply {
            adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, categories)
            background = activity.fondoRedondeado(activity.superficie2, 12)
            setPadding(activity.dp(12), activity.dp(10), activity.dp(12), activity.dp(10))
        }

        val inputYear = EditText(activity).apply {
            hint = "Año (opcional, ej: 2026)"
            setHintTextColor(activity.gris)
            setTextColor(activity.blanco)
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(activity.dp(16), activity.dp(14), activity.dp(16), activity.dp(14))
            background = activity.fondoRedondeado(activity.superficie2, 12)
        }

        layout.addView(inputTitle, activity.margenAbajo(12))
        layout.addView(inputDesc, activity.margenAbajo(12))
        layout.addView(spinnerCategory, activity.margenAbajo(12))
        layout.addView(inputYear, activity.margenAbajo(8))

        AlertDialog.Builder(activity)
            .setTitle("Subir Video Independiente")
            .setView(layout)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Seleccionar y Subir") { _, _ ->
                val title = inputTitle.text.toString().trim()
                if (title.isBlank()) {
                    Toast.makeText(activity, "El título del video es obligatorio", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                activity.targetIndependentTitle = title
                activity.targetIndependentDesc = inputDesc.text.toString().trim()
                activity.targetIndependentCategory = spinnerCategory.selectedItem.toString()
                activity.targetIndependentYear = inputYear.text.toString().trim()
                activity.targetMediaTypeForUpload = "independent"
                activity.targetTmdbIdForUpload = ""

                activity.selectorVideo.launch(arrayOf("video/*"))
            }
            .show()
    }
}

package com.example.venus.ui.library

import android.net.Uri
import android.widget.*
import com.example.venus.MainActivity

@androidx.media3.common.util.UnstableApi
class LocalLibraryScreen(private val activity: MainActivity) {

    fun mostrarBiblioteca() {
        activity.seleccionarNavegacion(3)
        val pantalla = activity.prepararPantalla()
        val videos = activity.almacen.obtenerVideos()

        activity.etiqueta(pantalla, "Tu colección personal")
        activity.titulo(pantalla, "Biblioteca local", 27f)
        activity.texto(pantalla, "${videos.size} videos importados en este dispositivo")

        activity.boton(pantalla, "IMPORTAR VIDEO LOCAL", {
            activity.selectorVideo.launch(arrayOf("video/*"))
        })

        if (videos.isEmpty()) {
            activity.tarjeta(
                pantalla,
                "Tu biblioteca local esta vacia",
                "Pulsa Importar video local, elige un archivo y aparecera aqui."
            )
        } else {
            videos.forEach { uriTexto ->
                val nombre = activity.nombreArchivo(Uri.parse(uriTexto))

                activity.tarjetaVideo(
                    contenedor = pantalla,
                    uriTexto = uriTexto,
                    nombre = nombre,
                    accion = { activity.reproducirVideo(uriTexto) },
                    alMantener = { activity.eliminarReferencia(uriTexto) }
                )
            }
        }

        activity.texto(
            pantalla,
            "Si mueves o eliminas un archivo desde otra aplicacion, VENUS podria perder el acceso a el.",
            activity.gris,
            11f
        )
    }
}

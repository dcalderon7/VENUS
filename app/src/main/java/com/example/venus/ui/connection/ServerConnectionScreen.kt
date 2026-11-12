package com.example.venus.ui.connection

import android.widget.Toast
import com.example.venus.MainActivity

@androidx.media3.common.util.UnstableApi
class ServerConnectionScreen(private val activity: MainActivity) {

    fun mostrarConexion() {
        activity.seleccionarNavegacion(0)
        val pantalla = activity.prepararPantalla()

        activity.etiqueta(pantalla, "Ecosistema VENUS")
        activity.titulo(pantalla, "Servidor PC", 27f)

        val estaConectado = activity.almacen.estaServidorConectado()

        if (estaConectado) {
            val nombreServidor = activity.almacen.obtenerNombreServidor()
            val ipServidor = activity.almacen.obtenerIpServidor()

            activity.tarjeta(
                pantalla,
                "Conectado: $nombreServidor",
                "Direccion: $ipServidor\nTransmision activa via Wi-Fi por red local."
            )

            activity.etiqueta(pantalla, "SERVICIOS DEL SERVIDOR")
            activity.titulo(pantalla, "Catalogo compartido desde tu PC", 19f)

            activity.catalogosMock.take(3).forEach { pelicula ->
                activity.tarjetaVideo(
                    contenedor = pantalla,
                    uriTexto = pelicula.videoUrl,
                    nombre = "[PC] ${pelicula.titulo}",
                    accion = { activity.reproducirDemo(pelicula) },
                    alMantener = {}
                )
            }

            activity.boton(pantalla, "DESCONECTAR SERVIDOR", {
                activity.almacen.desconectarServidor()
                Toast.makeText(activity, "Servidor desconectado", Toast.LENGTH_SHORT).show()
                mostrarConexion()
            }, secundario = true)

        } else {
            activity.tarjeta(
                pantalla,
                "Servidor no vinculado",
                "Vincula tu computadora con VENUS para transmitir tus peliculas y series guardadas en tu PC directamente a tu telefono."
            )

            activity.boton(pantalla, "VINCULAR SERVIDOR VENUS (QR / IP)", {
                activity.mostrarDialogoVinculacion()
            })
        }

        activity.boton(pantalla, "VOLVER AL INICIO", {
            activity.mostrarInicio()
        }, secundario = true)
    }
}

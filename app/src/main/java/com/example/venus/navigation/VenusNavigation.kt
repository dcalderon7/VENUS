package com.example.venus.navigation

import com.example.venus.MainActivity

@androidx.media3.common.util.UnstableApi
class VenusNavigation(private val activity: MainActivity) {
    fun navigateToHome() {
        activity.runOnUiThread { activity.mostrarInicio() }
    }

    fun navigateToExplore() {
        activity.runOnUiThread { activity.mostrarExplorar() }
    }

    fun navigateToMyList() {
        activity.runOnUiThread { activity.mostrarMiLista() }
    }

    fun navigateToProfile() {
        activity.runOnUiThread { activity.mostrarPerfil() }
    }

    fun navigateToLocalLibrary() {
        activity.runOnUiThread { activity.mostrarBiblioteca() }
    }

    fun navigateToServerLibrary() {
        activity.runOnUiThread { activity.mostrarMiBibliotecaIndependiente() }
    }

    fun navigateToAuth() {
        activity.runOnUiThread { activity.mostrarPantallaAutenticacion() }
    }

    fun navigateToPasswordRecovery() {
        activity.runOnUiThread { activity.mostrarPantallaRecuperarPassword() }
    }

    fun navigateToCompleteReset(token: String) {
        activity.runOnUiThread { activity.mostrarPantallaCompletarRecuperacion(token) }
    }
}

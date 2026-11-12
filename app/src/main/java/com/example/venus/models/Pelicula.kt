package com.example.venus.models

data class Episodio(
    val numero: Int,
    val titulo: String,
    val duracion: String,
    val sinopsis: String,
    val videoUrl: String
)

data class Pelicula(
    val id: String,
    val titulo: String,
    val categoria: String,
    val calificacion: String,
    val duracion: String,
    val descripcion: String,
    val imageUrl: String,
    val videoUrl: String,
    val esSerie: Boolean = false,
    val episodios: List<Episodio> = emptyList()
)
typealias PeliculaMock = Pelicula
typealias EpisodioMock = Episodio

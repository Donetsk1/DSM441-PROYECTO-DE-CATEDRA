package com.example.dsmproyectodecatedra.data

data class UserProfile(
    val uid: String = "",
    val nombreCompleto: String = "",
    val correo: String = "",
    val carnet: String = "",
    val carrera: String = "",
    val moneda: String = "USD"
)

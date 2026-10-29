package com.example.dsmproyectodecatedra.data

data class UserProfile(
    val uid: String = "",
    val nombreCompleto: String = "",
    val correo: String = "",
    val carnet: String = "",
    val carrera: String = "",
    val moneda: String = "USD"
) {
    fun databaseValues(): Map<String, Any> = mapOf(
        "uid" to uid,
        "nombreCompleto" to nombreCompleto.trim(),
        "correo" to correo.trim(),
        "carnet" to carnet.trim(),
        "carrera" to carrera.trim(),
        "moneda" to moneda
    )

    companion object {
        val supportedCurrencies = setOf("USD", "GTQ", "HNL", "NIO", "CRC")

        fun fromDatabase(uid: String, email: String, data: Map<*, *>): UserProfile = UserProfile(
            uid = uid,
            nombreCompleto = data["nombreCompleto"] as? String ?: "",
            correo = email.ifBlank { data["correo"] as? String ?: "" },
            carnet = data["carnet"] as? String ?: "",
            carrera = data["carrera"] as? String ?: "",
            moneda = (data["moneda"] as? String)?.takeIf { it in supportedCurrencies } ?: "USD"
        )
    }
}

package com.example.dsmproyectodecatedra.history

import com.example.dsmproyectodecatedra.data.FinancialMovement
import java.text.Normalizer
import java.util.Locale

data class HistoryFilter(
    val month: String? = null,
    val type: String? = null,
    val query: String = "",
    val category: String? = null
) {
    fun apply(movements: List<FinancialMovement>): List<FinancialMovement> {
        val search = normalize(query.trim().removePrefix("#"))
        return movements.filter { movement ->
            (month == null || movement.date.take(7) == month) &&
                (type == null || movement.type == type) &&
                (category == null || movement.category == category) &&
                (search.isEmpty() || listOf(movement.description, movement.category, movement.paymentMethod)
                    .plus(movement.tags).any { normalize(it).contains(search) })
        }.sortedWith(compareByDescending<FinancialMovement> { it.date }.thenByDescending { it.createdAt })
    }

    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)
}

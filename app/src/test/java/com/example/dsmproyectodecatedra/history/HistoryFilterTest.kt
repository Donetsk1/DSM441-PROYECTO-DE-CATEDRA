package com.example.dsmproyectodecatedra.history

import com.example.dsmproyectodecatedra.data.FinancialMovement
import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryFilterTest {
    private val expense = FinancialMovement("expense", "expense", 850, "Alimentación", "2026-10-25", "Efectivo", "Almuerzo cafetería", listOf("universidad"), "USD", 100)
    private val income = expense.copy(id = "income", type = "income", category = "Beca", description = "Beca mensual", tags = emptyList())
    private val oldExpense = expense.copy(id = "old", date = "2026-09-30")
    private val movements = listOf(oldExpense, income, expense)

    @Test
    fun combinesMonthTypeAndCategory() {
        assertEquals(listOf(expense), HistoryFilter("2026-10", "expense", category = "Alimentación").apply(movements))
    }

    @Test
    fun findsDescriptionWithoutAccentsOrCaseSensitivity() {
        assertEquals(listOf(expense), HistoryFilter("2026-10", query = "CAFETERIA").apply(movements))
    }

    @Test
    fun searchesTagsWithOptionalHashPrefix() {
        assertEquals(listOf(expense), HistoryFilter("2026-10", query = "#UNIVERSIDAD").apply(movements))
    }

    @Test
    fun allMonthsIncludesOlderMovementsAndOrdersByDateThenCreationTime() {
        val recentExpense = expense.copy(id = "recent", createdAt = 200)
        assertEquals(listOf("recent", "expense", "old"), HistoryFilter(type = "expense")
            .apply(listOf(oldExpense, expense, recentExpense)).map { it.id })
    }

    @Test
    fun unmatchedQueryReturnsEmptyHistory() {
        assertEquals(emptyList<FinancialMovement>(), HistoryFilter(query = "no existe").apply(movements))
    }
}

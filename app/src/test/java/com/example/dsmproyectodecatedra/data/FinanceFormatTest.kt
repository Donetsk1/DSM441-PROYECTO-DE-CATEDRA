package com.example.dsmproyectodecatedra.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FinanceFormatTest {
    @Test
    fun monetaryInputPreservesCentsAndRejectsInvalidAmounts() {
        assertEquals(850L, FinanceFormat.cents("8.50"))
        assertEquals(850L, FinanceFormat.cents("8,50"))
        assertEquals(1L, FinanceFormat.cents("0.01"))
        listOf("", "0", "-1", "NaN", "1.001", "1e3", "1,000.00").forEach {
            assertNull(FinanceFormat.cents(it))
        }
    }

    @Test
    fun spendingIncludesOnlyMatchingExpensesMonthCategoryAndCurrency() {
        val budget = CategoryBudget("b", "Alimentación", "2026-10", 6000, "USD", 0)
        val base = FinancialMovement("a", "expense", 850, "Alimentación", "2026-10-25", "", "", emptyList(), "USD", 0)
        val movements = listOf(base, base.copy(id = "b", amountCents = 100),
            base.copy(type = "income"), base.copy(date = "2026-09-30"),
            base.copy(category = "Transporte"), base.copy(currency = "GTQ"))
        assertEquals(950L, FinanceFormat.spent(budget, movements))
        assertEquals(100L, FinanceFormat.spent(budget, movements.drop(1)))
        assertEquals(0L, FinanceFormat.spent(budget, emptyList()))
    }

    @Test
    fun monthNavigationCrossesYearBoundaries() {
        assertEquals("2027-01", FinanceFormat.shiftMonth("2026-12", 1))
        assertEquals("2025-12", FinanceFormat.shiftMonth("2026-01", -1))
    }

    @Test
    fun budgetUsageRetainsExceededPercentages() {
        assertEquals(75, FinanceFormat.percentage(4500, 6000))
        assertEquals(100, FinanceFormat.percentage(6000, 6000))
        assertEquals(110, FinanceFormat.percentage(6600, 6000))
        assertEquals(0, FinanceFormat.percentage(0, 6000))
    }
}

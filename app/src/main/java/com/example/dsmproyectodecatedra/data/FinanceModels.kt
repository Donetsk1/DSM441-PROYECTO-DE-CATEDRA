package com.example.dsmproyectodecatedra.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Currency
import java.util.Locale

data class FinancialMovement(
    val id: String,
    val type: String,
    val amountCents: Long,
    val category: String,
    val date: String,
    val paymentMethod: String,
    val description: String,
    val tags: List<String>,
    val currency: String,
    val createdAt: Long
)

data class CategoryBudget(
    val id: String,
    val category: String,
    val month: String,
    val limitCents: Long,
    val currency: String,
    val createdAt: Long
)

data class FinanceSnapshot(
    val movements: List<FinancialMovement> = emptyList(),
    val budgets: List<CategoryBudget> = emptyList(),
    val currency: String = "USD"
)

data class MovementDraft(
    var id: String? = null,
    var type: String = "expense",
    var amount: String = "",
    var category: String = "Alimentación",
    var date: String = FinanceFormat.today(),
    var paymentMethod: String = "",
    var description: String = "",
    var tags: List<String> = emptyList(),
    var currency: String? = null,
    var createdAt: Long? = null
)

object FinanceFormat {
    private val locale = Locale.forLanguageTag("es-SV")

    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)

    fun month(): String = today().take(7)

    fun cents(value: String): Long? = try {
        val normalized = value.trim().replace(',', '.')
        if (!normalized.matches(Regex("\\d{1,9}(\\.\\d{1,2})?"))) null
        else BigDecimal(normalized).movePointRight(2).longValueExact().takeIf { it > 0 }
    } catch (_: ArithmeticException) {
        null
    }

    fun amount(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

    fun money(cents: Long, currency: String): String = NumberFormat.getCurrencyInstance(locale).apply {
        this.currency = Currency.getInstance(currency)
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }.format(BigDecimal.valueOf(cents, 2))

    fun dateLabel(date: String): String {
        val parsed = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date) ?: return date
        return SimpleDateFormat("d MMM yyyy", locale).format(parsed)
    }

    fun monthLabel(month: String): String {
        val parsed = SimpleDateFormat("yyyy-MM", Locale.US).parse(month) ?: return month
        return SimpleDateFormat("MMMM yyyy", locale).format(parsed).replaceFirstChar { it.titlecase(locale) }
    }

    fun shiftMonth(month: String, offset: Int): String {
        val calendar = Calendar.getInstance()
        calendar.time = SimpleDateFormat("yyyy-MM", Locale.US).parse(month)!!
        calendar.add(Calendar.MONTH, offset)
        return SimpleDateFormat("yyyy-MM", Locale.US).format(calendar.time)
    }

    fun spent(budget: CategoryBudget, movements: List<FinancialMovement>): Long = movements
        .filter { it.type == "expense" && it.category == budget.category &&
            it.date.take(7) == budget.month && it.currency == budget.currency }
        .sumOf { it.amountCents }

    fun percentage(spent: Long, limit: Long): Int = if (limit <= 0) 0 else
        BigDecimal.valueOf(spent).multiply(BigDecimal(100))
            .divide(BigDecimal.valueOf(limit), 0, RoundingMode.DOWN)
            .min(BigDecimal(Int.MAX_VALUE)).toInt()
}

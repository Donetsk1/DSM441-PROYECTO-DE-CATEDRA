package com.example.dsmproyectodecatedra.dashboard

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.dsmproyectodecatedra.R
import com.example.dsmproyectodecatedra.data.CategoryBudget
import com.example.dsmproyectodecatedra.data.FinanceFormat
import com.example.dsmproyectodecatedra.data.FinanceState
import com.example.dsmproyectodecatedra.data.FinanceUi
import com.example.dsmproyectodecatedra.data.FinanceViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class DashboardFragment : Fragment(R.layout.fragment_dashboard) {
    private val model: FinanceViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    render(view.findViewById(R.id.financeContent), state)
                    if (state.error != null || state.message != null) {
                        val notice = Snackbar.make(view, state.error ?: state.message.orEmpty(), Snackbar.LENGTH_LONG)
                        if (state.error != null) notice.setAction("Reintentar") { model.retry() }
                        notice.show()
                        model.clearNotice()
                    }
                }
            }
        }
    }

    private fun render(content: LinearLayout, state: FinanceState) {
        content.removeAllViews()
        val context = requireContext()
        val month = FinanceFormat.month()

        content.addView(FinanceUi.text(context, "Inicio", 24f, bold = true))
        content.addView(FinanceUi.text(context, FinanceFormat.monthLabel(month), 14f, FinanceUi.muted))

        if (state.loading) {
            content.addView(FinanceUi.text(context, "Cargando tu resumen financiero…", color = FinanceUi.muted))
            return
        }

        val data = state.data
        val currency = data.currency
        val movements = data.movements.filter { it.currency == currency }
        val balance = movements.sumOf { if (it.type == "income") it.amountCents else -it.amountCents }
        val monthMovements = movements.filter { it.date.take(7) == month }
        val monthIncome = monthMovements.filter { it.type == "income" }.sumOf { it.amountCents }
        val monthExpense = monthMovements.filter { it.type == "expense" }.sumOf { it.amountCents }

        val balanceBody = FinanceUi.column(context, 24).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#1C8ADE"),
                    Color.parseColor("#17A999"),
                    Color.parseColor("#2E9E3D"),
                    Color.parseColor("#D6DE2E")
                )
            ).apply { cornerRadius = FinanceUi.dp(context, 20).toFloat() }
            (layoutParams as LinearLayout.LayoutParams).bottomMargin = FinanceUi.dp(context, 12)
        }
        balanceBody.addView(FinanceUi.text(context, "Saldo disponible", 13f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            alpha = 0.9f
        })
        balanceBody.addView(FinanceUi.text(context, FinanceFormat.money(balance, currency), 32f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
        })
        content.addView(balanceBody)

        val summaryRow = LinearLayout(context)
        summaryRow.addView(FinanceUi.card(context, summaryColumn(context, "Ingresos del mes", monthIncome, currency, FinanceUi.green))
            .apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = FinanceUi.dp(context, 8) } })
        summaryRow.addView(FinanceUi.card(context, summaryColumn(context, "Gastos del mes", monthExpense, currency, FinanceUi.orange))
            .apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        content.addView(summaryRow)

        content.addView(FinanceUi.text(context, "Accesos rápidos", 16f, bold = true))
        val actionsRow = LinearLayout(context)
        actionsRow.addView(FinanceUi.button(context, "+ Nuevo movimiento", true) { goToTab(R.id.navTransactions) }
            .apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = FinanceUi.dp(context, 8) } })
        actionsRow.addView(FinanceUi.button(context, "Ver presupuestos") { goToTab(R.id.navBudget) }
            .apply { layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
        content.addView(actionsRow)

        content.addView(FinanceUi.text(context, "Alertas importantes", 16f, bold = true))
        val budgets = data.budgets.filter { it.month == month && it.currency == currency }
        val alerts = budgets.map { it to FinanceFormat.spent(it, movements) }
            .filter { FinanceFormat.percentage(it.second, it.first.limitCents) >= 75 }
            .sortedByDescending { FinanceFormat.percentage(it.second, it.first.limitCents) }
        if (alerts.isEmpty()) {
            val empty = FinanceUi.column(context, 18)
            empty.addView(FinanceUi.text(context, "Todo bajo control", 15f, FinanceUi.green, true))
            empty.addView(FinanceUi.text(context, "No tienes presupuestos cerca del límite ni excedidos este mes.", color = FinanceUi.muted))
            content.addView(FinanceUi.card(context, empty))
        } else {
            alerts.forEach { (budget, spent) -> content.addView(alertCard(context, budget, spent)) }
        }
    }

    private fun summaryColumn(context: android.content.Context, label: String, cents: Long, currency: String, color: Int): LinearLayout {
        val column = FinanceUi.column(context, 18)
        column.addView(FinanceUi.text(context, label, 12f, FinanceUi.muted))
        column.addView(FinanceUi.text(context, FinanceFormat.money(cents, currency), 18f, color, true))
        return column
    }

    private fun alertCard(context: android.content.Context, budget: CategoryBudget, spent: Long): View {
        val exceeded = spent > budget.limitCents
        val percentage = FinanceFormat.percentage(spent, budget.limitCents)
        val body = FinanceUi.column(context, 18)
        body.addView(FinanceUi.text(context, if (exceeded) "Presupuesto excedido" else "Cerca del límite", 15f,
            if (exceeded) FinanceUi.orange else Color.rgb(145, 108, 0), true))
        body.addView(FinanceUi.text(context,
            "${budget.category}: ${FinanceFormat.money(spent, budget.currency)} de ${FinanceFormat.money(budget.limitCents, budget.currency)} ($percentage%).",
            14f, FinanceUi.ink))
        return FinanceUi.card(context, body,
            if (exceeded) Color.rgb(255, 239, 231) else Color.rgb(255, 249, 231),
            if (exceeded) Color.rgb(255, 195, 173) else Color.rgb(234, 216, 143))
    }

    private fun goToTab(itemId: Int) {
        requireActivity().findViewById<BottomNavigationView>(R.id.bottomNavigation).selectedItemId = itemId
    }
}

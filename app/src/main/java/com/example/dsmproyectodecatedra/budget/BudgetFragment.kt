package com.example.dsmproyectodecatedra.budget

import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.dsmproyectodecatedra.R
import com.example.dsmproyectodecatedra.data.CategoryBudget
import com.example.dsmproyectodecatedra.data.FinanceFormat
import com.example.dsmproyectodecatedra.data.FinanceSnapshot
import com.example.dsmproyectodecatedra.data.FinanceState
import com.example.dsmproyectodecatedra.data.FinanceUi
import com.example.dsmproyectodecatedra.data.FinanceViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import java.util.UUID

class BudgetFragment : Fragment(R.layout.fragment_budget) {
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

    private fun refresh() {
        view?.findViewById<LinearLayout>(R.id.financeContent)?.let { render(it, model.state.value) }
    }

    private fun render(content: LinearLayout, state: FinanceState) {
        content.removeAllViews()
        val context = requireContext()
        if (model.showAlerts) {
            renderAlerts(content, state.data)
            return
        }
        content.addView(FinanceUi.text(context, "Presupuestos", 24f, bold = true))
        val actions = LinearLayout(context)
        actions.addView(FinanceUi.button(context, "+ Nuevo", true) { showBudgetDialog(null) }.apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = FinanceUi.dp(context, 8) }
            isEnabled = !state.loading && !state.busy
        })
        actions.addView(FinanceUi.button(context, "Alertas") { model.showAlerts = true; refresh() }.apply {
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        content.addView(actions)
        val monthRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
        monthRow.addView(FinanceUi.button(context, "‹") {
            model.selectedMonth = FinanceFormat.shiftMonth(model.selectedMonth, -1)
            refresh()
        }.apply { contentDescription = "Mes anterior"; layoutParams = LinearLayout.LayoutParams(FinanceUi.dp(context, 56), -2) })
        monthRow.addView(FinanceUi.text(context, FinanceFormat.monthLabel(model.selectedMonth), 16f, bold = true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        monthRow.addView(FinanceUi.button(context, "›") {
            model.selectedMonth = FinanceFormat.shiftMonth(model.selectedMonth, 1)
            refresh()
        }.apply { contentDescription = "Mes siguiente"; layoutParams = LinearLayout.LayoutParams(FinanceUi.dp(context, 56), -2) })
        content.addView(monthRow)
        if (state.loading) {
            content.addView(FinanceUi.text(context, "Cargando presupuestos…", color = FinanceUi.muted))
            return
        }
        val budgets = monthBudgets(state.data)
        val summary = FinanceUi.column(context, 20)
        summary.addView(FinanceUi.text(context, "Total presupuestado · ${state.data.currency}", 13f, FinanceUi.muted).apply { gravity = Gravity.CENTER })
        summary.addView(FinanceUi.text(context, FinanceFormat.money(budgets.sumOf { it.limitCents }, state.data.currency), 30f, bold = true).apply { gravity = Gravity.CENTER })
        summary.addView(FinanceUi.text(context,
            "Gastado ${FinanceFormat.money(budgets.sumOf { FinanceFormat.spent(it, state.data.movements) }, state.data.currency)}",
            14f, FinanceUi.muted).apply { gravity = Gravity.CENTER })
        content.addView(FinanceUi.card(context, summary))
        if (budgets.isEmpty()) {
            val empty = FinanceUi.column(context, 20)
            empty.addView(FinanceUi.text(context, "Organiza tu mes", 19f, bold = true))
            empty.addView(FinanceUi.text(context, "Todavía no tienes presupuestos para este mes. Crea un límite por categoría y tus gastos se calcularán automáticamente.", color = FinanceUi.muted))
            content.addView(FinanceUi.card(context, empty))
        }
        budgets.forEach { budget ->
            val spent = FinanceFormat.spent(budget, state.data.movements)
            val percentage = FinanceFormat.percentage(spent, budget.limitCents)
            val color = when {
                spent > budget.limitCents -> FinanceUi.orange
                percentage >= 75 -> FinanceUi.yellow
                else -> FinanceUi.green
            }
            val body = FinanceUi.column(context, 18)
            val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            heading.addView(FinanceUi.text(context, budget.category, 16f, bold = true).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            heading.addView(FinanceUi.text(context, "$percentage%", 14f, color, true).apply { layoutParams = LinearLayout.LayoutParams(-2, -2) })
            body.addView(heading)
            body.addView(FinanceUi.text(context,
                "${FinanceFormat.money(spent, budget.currency)} / ${FinanceFormat.money(budget.limitCents, budget.currency)}",
                13f, FinanceUi.muted))
            body.addView(LinearProgressIndicator(context).apply {
                max = 100
                progress = percentage.coerceIn(0, 100)
                setIndicatorColor(color)
                trackColor = FinanceUi.line
                trackThickness = FinanceUi.dp(context, 7)
                trackCornerRadius = FinanceUi.dp(context, 4)
                layoutParams = LinearLayout.LayoutParams(-1, FinanceUi.dp(context, 12))
            })
            if (spent > budget.limitCents) {
                body.addView(FinanceUi.text(context, "¡Excedido!", 13f, FinanceUi.orange, true))
            } else if (percentage >= 75) {
                body.addView(FinanceUi.text(context, "Cerca del límite", 13f, FinanceUi.muted))
            }
            content.addView(FinanceUi.card(context, body,
                if (spent > budget.limitCents) Color.rgb(255, 239, 231) else Color.WHITE,
                if (spent > budget.limitCents) Color.rgb(255, 195, 173) else FinanceUi.line).apply {
                isClickable = true
                isFocusable = true
                contentDescription = "Editar presupuesto de ${budget.category}, $percentage por ciento utilizado"
                setOnClickListener { if (!state.busy) showBudgetDialog(budget) }
            })
        }
        content.addView(FinanceUi.text(context, "Toca un presupuesto para editarlo o eliminarlo.", 12f, FinanceUi.muted))
        content.addView(FinanceUi.button(context, "Actualizar") { model.retry() }.apply { isEnabled = !state.busy })
    }

    private fun monthBudgets(data: FinanceSnapshot) = data.budgets
        .filter { it.month == model.selectedMonth && it.currency == data.currency }
        .sortedBy { it.category }

    private fun renderAlerts(content: LinearLayout, data: FinanceSnapshot) {
        val context = requireContext()
        content.addView(FinanceUi.button(context, "Volver a presupuestos") { model.showAlerts = false; refresh() })
        content.addView(FinanceUi.text(context, "Alertas", 24f, bold = true))
        content.addView(FinanceUi.text(context, FinanceFormat.monthLabel(model.selectedMonth), 14f, FinanceUi.muted))
        val budgets = monthBudgets(data)
        val warnings = budgets.map { it to FinanceFormat.spent(it, data.movements) }
            .filter { FinanceFormat.percentage(it.second, it.first.limitCents) >= 75 }
            .sortedByDescending { FinanceFormat.percentage(it.second, it.first.limitCents) }
        warnings.forEach { (budget, spent) ->
            val exceeded = spent > budget.limitCents
            val body = FinanceUi.column(context, 18)
            body.addView(FinanceUi.text(context, if (exceeded) "Presupuesto excedido" else "Cerca del límite", 16f,
                if (exceeded) FinanceUi.orange else Color.rgb(145, 108, 0), true))
            body.addView(FinanceUi.text(context,
                "${budget.category}: gastaste ${FinanceFormat.money(spent, budget.currency)} de ${FinanceFormat.money(budget.limitCents, budget.currency)} (${FinanceFormat.percentage(spent, budget.limitCents)}%).",
                14f, FinanceUi.ink))
            content.addView(FinanceUi.card(context, body,
                if (exceeded) Color.rgb(255, 239, 231) else Color.rgb(255, 249, 231),
                if (exceeded) Color.rgb(255, 195, 173) else Color.rgb(234, 216, 143)))
        }
        if (warnings.isEmpty()) content.addView(FinanceUi.text(context, "No hay presupuestos cerca del límite ni excedidos.", color = FinanceUi.muted))
        budgets.sortedByDescending { it.createdAt }.forEach { budget ->
            val body = FinanceUi.column(context, 18)
            body.addView(FinanceUi.text(context, "Presupuesto creado", 16f, FinanceUi.green, true))
            body.addView(FinanceUi.text(context, "Definiste un límite de ${FinanceFormat.money(budget.limitCents, budget.currency)} para ${budget.category}."))
            content.addView(FinanceUi.card(context, body))
        }
    }

    private fun showBudgetDialog(existing: CategoryBudget?) {
        val context = requireContext()
        val state = model.state.value
        val container = FinanceUi.column(context, 20)
        container.addView(FinanceUi.text(context, FinanceFormat.monthLabel(model.selectedMonth), 15f, FinanceUi.muted))
        container.addView(FinanceUi.text(context, "Categoría", 13f, FinanceUi.muted))
        val options = (FinanceUi.expenseCategories + state.data.movements.filter { it.type == "expense" }.map { it.category } +
            listOfNotNull(existing?.category)).distinct()
        val category = Spinner(context).apply {
            adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, options) {
                override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
                    super.getView(position, convertView, parent).apply { (this as? TextView)?.setTextColor(FinanceUi.ink) }
            }
            layoutParams = LinearLayout.LayoutParams(-1, FinanceUi.dp(context, 56))
            setSelection(options.indexOf(existing?.category).coerceAtLeast(0))
            isEnabled = existing == null
        }
        container.addView(category)
        val amount = FinanceUi.field(context, "Límite mensual (${existing?.currency ?: state.data.currency})",
            existing?.let { FinanceFormat.amount(it.limitCents) }.orEmpty(),
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        amount.editText?.filters = arrayOf(InputFilter.LengthFilter(12))
        container.addView(amount)
        val dialog = MaterialAlertDialogBuilder(context)
            .setTitle(if (existing == null) "Nuevo presupuesto" else "Editar presupuesto")
            .setView(container).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null)
            .apply { if (existing != null) setNeutralButton("Eliminar") { _, _ -> confirmDelete(existing) } }
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val cents = FinanceFormat.cents(amount.editText?.text.toString())
                val selectedCategory = category.selectedItem.toString()
                if (cents == null) {
                    amount.error = "Ingresa un monto mayor que cero, con hasta 2 decimales"
                    return@setOnClickListener
                }
                if (existing == null && monthBudgets(model.state.value.data).any { it.category.equals(selectedCategory, true) }) {
                    amount.error = "Ya tienes un presupuesto para esta categoría. Tócalo para editarlo."
                    return@setOnClickListener
                }
                val currency = existing?.currency ?: state.data.currency
                val id = existing?.id ?: UUID.nameUUIDFromBytes("${model.selectedMonth}|$selectedCategory|$currency".toByteArray(Charsets.UTF_8)).toString()
                model.saveBudget(CategoryBudget(id, selectedCategory, model.selectedMonth, cents, currency,
                    existing?.createdAt ?: System.currentTimeMillis()))
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    private fun confirmDelete(budget: CategoryBudget) {
        MaterialAlertDialogBuilder(requireContext()).setTitle("Eliminar presupuesto")
            .setMessage("¿Eliminar el presupuesto de ${budget.category}? Tus movimientos se conservarán.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ -> model.deleteBudget(budget.id) }.show()
    }
}

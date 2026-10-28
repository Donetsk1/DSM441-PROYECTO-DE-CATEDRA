package com.example.dsmproyectodecatedra.history

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.dsmproyectodecatedra.R
import com.example.dsmproyectodecatedra.data.FinanceFormat
import com.example.dsmproyectodecatedra.data.FinanceState
import com.example.dsmproyectodecatedra.data.FinanceUi
import com.example.dsmproyectodecatedra.data.FinanceViewModel
import com.example.dsmproyectodecatedra.data.FinancialMovement
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class HistoryFragment : Fragment(R.layout.fragment_history) {
    private val model: FinanceViewModel by viewModels()

    private var month = FinanceFormat.month()
    private var typeFilter: String? = null          // null = todos, "income", "expense"
    private var query = ""
    private var state = FinanceState()

    private var monthLabel: android.widget.TextView? = null
    private var summary: LinearLayout? = null
    private var list: LinearLayout? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        val content = view.findViewById<LinearLayout>(R.id.historyContent)
        content.addView(FinanceUi.text(ctx, "Historial", 23f, bold = true))

        // --- Navegación por mes ---
        val nav = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val prev = FinanceUi.button(ctx, "‹") { month = FinanceFormat.shiftMonth(month, -1); render() }
        val next = FinanceUi.button(ctx, "›") { month = FinanceFormat.shiftMonth(month, 1); render() }
        monthLabel = FinanceUi.text(ctx, "", 16f, bold = true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        listOf(prev, next).forEach { it.layoutParams = LinearLayout.LayoutParams(FinanceUi.dp(ctx, 64), -2) }
        nav.addView(prev); nav.addView(monthLabel); nav.addView(next)
        content.addView(nav)

        // --- Resumen del mes ---
        summary = FinanceUi.column(ctx, 18)
        content.addView(FinanceUi.card(ctx, summary!!))

        // --- Búsqueda ---
        content.addView(FinanceUi.field(ctx, "Buscar (descripción, categoría, etiqueta)").apply {
            editText?.filters = arrayOf(InputFilter.LengthFilter(40))
            editText?.doAfterTextChanged { query = it.toString().trim(); render() }
        })

        // --- Filtro por tipo ---
        val chips = ChipGroup(ctx).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = FinanceUi.dp(ctx, 12) }
        }
        listOf("Todos" to null, "Ingresos" to "income", "Gastos" to "expense").forEach { (label, type) ->
            chips.addView(Chip(ctx).apply {
                text = label
                isCheckable = true
                isChecked = type == typeFilter
                setTextColor(FinanceUi.ink)
                chipBackgroundColor = ColorStateList.valueOf(Color.WHITE)
                setOnClickListener { typeFilter = type; render() }
            })
        }
        content.addView(chips)

        // --- Lista ---
        list = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        content.addView(list)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { s ->
                    state = s
                    render()
                    if (s.error != null || s.message != null) {
                        val notice = Snackbar.make(view, s.error ?: s.message.orEmpty(), Snackbar.LENGTH_LONG)
                        if (s.error != null) notice.setAction("Reintentar") { model.retry() }
                        notice.show()
                        model.clearNotice()
                    }
                }
            }
        }
    }

    private fun render() {
        val ctx = context ?: return
        val list = list ?: return
        val summary = summary ?: return
        monthLabel?.text = FinanceFormat.monthLabel(month)
        list.removeAllViews()
        summary.removeAllViews()

        if (state.loading) {
            list.addView(FinanceUi.text(ctx, "Cargando…", 14f, FinanceUi.muted))
            return
        }

        val ofMonth = state.data.movements.filter { it.date.take(7) == month }

        // Resumen agrupado por moneda (por si el usuario cambió de moneda en algún momento)
        if (ofMonth.isEmpty()) {
            summary.addView(FinanceUi.text(ctx, "Sin movimientos este mes", 14f, FinanceUi.muted))
        } else ofMonth.groupBy { it.currency }.forEach { (currency, items) ->
            val income = items.filter { it.type == "income" }.sumOf { it.amountCents }
            val expense = items.filter { it.type == "expense" }.sumOf { it.amountCents }
            summary.addView(FinanceUi.text(ctx, "Ingresos: ${FinanceFormat.money(income, currency)}", 14f, FinanceUi.green, true))
            summary.addView(FinanceUi.text(ctx, "Gastos: ${FinanceFormat.money(expense, currency)}", 14f, FinanceUi.orange, true))
            val balance = income - expense
            summary.addView(FinanceUi.text(ctx,
                "Balance: " + (if (balance < 0) "−" else "") + FinanceFormat.money(kotlin.math.abs(balance), currency), 16f, bold = true))
        }

        val filtered = ofMonth
            .filter { typeFilter == null || it.type == typeFilter }
            .filter {
                query.isEmpty() || it.description.contains(query, true) || it.category.contains(query, true) ||
                        it.tags.any { tag -> tag.contains(query.removePrefix("#"), true) }
            }
            .sortedWith(compareByDescending<FinancialMovement> { it.date }.thenByDescending { it.createdAt })

        if (filtered.isEmpty() && ofMonth.isNotEmpty()) {
            list.addView(FinanceUi.text(ctx, "Ningún movimiento coincide con los filtros.", 14f, FinanceUi.muted))
        }
        filtered.forEach { list.addView(row(it)) }
    }

    private fun row(m: FinancialMovement): View {
        val ctx = requireContext()
        val color = if (m.type == "income") FinanceUi.green else FinanceUi.orange
        val box = FinanceUi.column(ctx, 16)
        box.addView(FinanceUi.text(ctx, m.description.ifBlank { m.category }, 15f, bold = true))
        box.addView(FinanceUi.text(ctx, "${m.category} · ${FinanceFormat.dateLabel(m.date)}", 12f, FinanceUi.muted))
        box.addView(FinanceUi.text(ctx,
            (if (m.type == "income") "+" else "−") + FinanceFormat.money(m.amountCents, m.currency), 18f, color, true))
        return FinanceUi.card(ctx, box).apply { setOnClickListener { showDetail(m) } }
    }

    private fun showDetail(m: FinancialMovement) {
        val body = listOf(
            "${if (m.type == "income") "Ingreso" else "Gasto"}: ${FinanceFormat.money(m.amountCents, m.currency)}",
            "Categoría: ${m.category}",
            "Fecha: ${FinanceFormat.dateLabel(m.date)}",
            "Método de pago: ${m.paymentMethod.ifBlank { "Sin especificar" }}",
            "Descripción: ${m.description.ifBlank { "Sin descripción" }}",
            "Etiquetas: ${m.tags.joinToString(" ") { "#$it" }.ifBlank { "Sin etiquetas" }}"
        ).joinToString("\n")
        MaterialAlertDialogBuilder(requireContext()).setTitle("Detalle del movimiento").setMessage(body)
            .setNegativeButton("Cerrar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                MaterialAlertDialogBuilder(requireContext()).setTitle("Eliminar movimiento")
                    .setMessage("¿Eliminar este movimiento? También se actualizará el gasto de tus presupuestos.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Eliminar") { _, _ -> model.deleteMovement(m.id) }.show()
            }.show()
    }

    override fun onDestroyView() {
        monthLabel = null; summary = null; list = null
        super.onDestroyView()
    }
}
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
import com.example.dsmproyectodecatedra.MainActivity
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
    private var typeFilter: String? = null
    private var query = ""
    private var categoryFilter: String? = null
    private var allMonths = false
    private var state = FinanceState()

    private var monthLabel: android.widget.TextView? = null
    private var summary: LinearLayout? = null
    private var list: LinearLayout? = null
    private var categoryButton: com.google.android.material.button.MaterialButton? = null
    private var monthsButton: com.google.android.material.button.MaterialButton? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            month = savedInstanceState.getString("historyMonth") ?: month
            typeFilter = savedInstanceState.getString("historyType")
            query = savedInstanceState.getString("historyQuery").orEmpty()
            categoryFilter = savedInstanceState.getString("historyCategory")
            allMonths = savedInstanceState.getBoolean("historyAllMonths")
        }
        val ctx = requireContext()
        val content = view.findViewById<LinearLayout>(R.id.historyContent)
        content.addView(FinanceUi.text(ctx, "Historial", 23f, bold = true))

        val nav = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(-1, -2)
        }
        val prev = FinanceUi.button(ctx, "‹") { month = FinanceFormat.shiftMonth(month, -1); allMonths = false; render() }
        val next = FinanceUi.button(ctx, "›") { month = FinanceFormat.shiftMonth(month, 1); allMonths = false; render() }
        prev.contentDescription = "Mes anterior"
        next.contentDescription = "Mes siguiente"
        monthLabel = FinanceUi.text(ctx, "", 16f, bold = true).apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        listOf(prev, next).forEach { it.layoutParams = LinearLayout.LayoutParams(FinanceUi.dp(ctx, 64), -2) }
        nav.addView(prev); nav.addView(monthLabel); nav.addView(next)
        content.addView(nav)

        summary = FinanceUi.column(ctx, 18)
        content.addView(FinanceUi.card(ctx, summary!!))

        val searchField = FinanceUi.field(ctx, "Buscar movimientos", query).apply {
            editText?.id = R.id.historySearch
            editText?.filters = arrayOf(InputFilter.LengthFilter(40))
            editText?.doAfterTextChanged { query = it.toString().trim(); render() }
        }
        content.addView(searchField)

        val chips = ChipGroup(ctx).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = FinanceUi.dp(ctx, 12) }
        }
        var allTypesId = View.NO_ID
        listOf("Todos" to null, "Ingresos" to "income", "Gastos" to "expense").forEach { (label, type) ->
            chips.addView(Chip(ctx).apply {
                id = View.generateViewId()
                if (type == null) allTypesId = id
                text = label
                isCheckable = true
                isChecked = type == typeFilter
                setTextColor(FinanceUi.ink)
                chipBackgroundColor = ColorStateList.valueOf(Color.WHITE)
                setOnClickListener { typeFilter = type; render() }
            })
        }
        content.addView(chips)
        categoryButton = FinanceUi.button(ctx, "Todas las categorías") {
            val options = listOf("Todas las categorías") + state.data.movements.map { it.category }.distinct().sorted()
            MaterialAlertDialogBuilder(ctx).setTitle("Filtrar por categoría")
                .setSingleChoiceItems(options.toTypedArray(), options.indexOf(categoryFilter).coerceAtLeast(0)) { dialog, index ->
                    categoryFilter = if (index == 0) null else options[index]
                    dialog.dismiss()
                    render()
                }.setNegativeButton("Cancelar", null).show()
        }
        content.addView(categoryButton)
        monthsButton = FinanceUi.button(ctx, "Todos los meses") { allMonths = !allMonths; render() }
        content.addView(monthsButton)
        content.addView(FinanceUi.button(ctx, "Limpiar filtros") {
            typeFilter = null
            categoryFilter = null
            allMonths = true
            query = ""
            chips.check(allTypesId)
            searchField.editText?.setText("")
            render()
        })

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
        monthLabel?.text = if (allMonths) "Todos los meses" else FinanceFormat.monthLabel(month)
        monthsButton?.text = if (allMonths) "Ver ${FinanceFormat.monthLabel(month)}" else "Todos los meses"
        categoryButton?.text = categoryFilter ?: "Todas las categorías"
        list.removeAllViews()
        summary.removeAllViews()

        if (state.loading) {
            list.addView(FinanceUi.text(ctx, "Cargando…", 14f, FinanceUi.muted))
            return
        }

        val ofMonth = state.data.movements.filter { allMonths || it.date.take(7) == month }
        summary.addView(FinanceUi.text(ctx, if (allMonths) "Resumen de todos los meses" else "Resumen del mes", 12f, FinanceUi.muted))

        if (ofMonth.isEmpty()) {
            summary.addView(FinanceUi.text(ctx, "Sin movimientos en este período", 14f, FinanceUi.muted))
        } else ofMonth.groupBy { it.currency }.forEach { (currency, items) ->
            val income = items.filter { it.type == "income" }.sumOf { it.amountCents }
            val expense = items.filter { it.type == "expense" }.sumOf { it.amountCents }
            summary.addView(FinanceUi.text(ctx, "Ingresos: ${FinanceFormat.money(income, currency)}", 14f, FinanceUi.green, true))
            summary.addView(FinanceUi.text(ctx, "Gastos: ${FinanceFormat.money(expense, currency)}", 14f, FinanceUi.orange, true))
            val balance = income - expense
            summary.addView(FinanceUi.text(ctx,
                "Balance: " + (if (balance < 0) "−" else "") + FinanceFormat.money(kotlin.math.abs(balance), currency), 16f, bold = true))
        }

        val filtered = HistoryFilter(if (allMonths) null else month, typeFilter, query, categoryFilter)
            .apply(state.data.movements)

        if (filtered.isEmpty()) {
            list.addView(FinanceUi.text(ctx, if (state.data.movements.isEmpty())
                "Tu historial está vacío. Registra tu primer ingreso o gasto para verlo aquí."
                else "Ningún movimiento coincide con los filtros. Prueba limpiar los filtros o consultar otro mes.", 14f, FinanceUi.muted))
        }
        var previousDate: String? = null
        filtered.forEach {
            if (it.date != previousDate) {
                list.addView(FinanceUi.text(ctx, FinanceFormat.dateLabel(it.date), 13f, FinanceUi.muted, true))
                previousDate = it.date
            }
            list.addView(row(it))
        }
    }

    private fun row(m: FinancialMovement): View {
        val ctx = requireContext()
        val color = if (m.type == "income") FinanceUi.green else FinanceUi.orange
        val box = FinanceUi.column(ctx, 16)
        box.addView(FinanceUi.text(ctx, m.description.ifBlank { m.category }, 15f, bold = true))
        box.addView(FinanceUi.text(ctx, "${m.category} · ${FinanceFormat.dateLabel(m.date)}", 12f, FinanceUi.muted))
        box.addView(FinanceUi.text(ctx,
            (if (m.type == "income") "+" else "−") + FinanceFormat.money(m.amountCents, m.currency), 18f, color, true))
        return FinanceUi.card(ctx, box).apply {
            isFocusable = true
            contentDescription = "Ver detalle de ${m.description.ifBlank { m.category }}"
            setOnClickListener { showDetail(m) }
        }
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
            .setPositiveButton("Editar") { _, _ -> (requireActivity() as MainActivity).openMovementEditor(m.id) }
            .setNeutralButton("Eliminar") { _, _ ->
                MaterialAlertDialogBuilder(requireContext()).setTitle("Eliminar movimiento")
                    .setMessage("¿Eliminar este movimiento? También se actualizará el gasto de tus presupuestos.")
                    .setNegativeButton("Cancelar", null)
                    .setPositiveButton("Eliminar") { _, _ -> if (!state.busy) model.deleteMovement(m.id) }.show()
            }.show()
    }

    override fun onDestroyView() {
        monthLabel = null; summary = null; list = null
        categoryButton = null
        monthsButton = null
        super.onDestroyView()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("historyMonth", month)
        outState.putString("historyType", typeFilter)
        outState.putString("historyQuery", query)
        outState.putString("historyCategory", categoryFilter)
        outState.putBoolean("historyAllMonths", allMonths)
        super.onSaveInstanceState(outState)
    }
}

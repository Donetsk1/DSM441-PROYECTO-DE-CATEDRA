package com.example.dsmproyectodecatedra.transactions

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class TransactionsFragment : Fragment(R.layout.fragment_transactions) {
    private val model: FinanceViewModel by viewModels()
    private var renderKey = ""
    private var amountField: TextInputLayout? = null
    private var saveButton: MaterialButton? = null
    private var currencyText: TextView? = null
    private var latestButton: MaterialButton? = null
    private var pendingEditId: String? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        renderKey = ""
        pendingEditId = arguments?.getString("editMovementId")
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    if (pendingEditId != null && !state.loading) {
                        val movement = state.data.movements.firstOrNull { it.id == pendingEditId }
                        pendingEditId = null
                        arguments?.remove("editMovementId")
                        if (movement != null) {
                            model.editMovement(movement)
                            return@collect
                        }
                    }
                    val key = "${state.detailId}:${System.identityHashCode(model.draft)}"
                    val content = view.findViewById<LinearLayout>(R.id.financeContent)
                    if (key != renderKey) {
                        renderKey = key
                        content.removeAllViews()
                        amountField = null
                        saveButton = null
                        currencyText = null
                        latestButton = null
                        if (state.detailId == null) renderForm(content, state)
                        else renderDetail(content, state)
                    } else if (state.detailId != null) {
                        content.removeAllViews()
                        renderDetail(content, state)
                    }
                    currencyText?.text = model.draft.currency ?: state.data.currency
                    latestButton?.isVisible = state.data.movements.isNotEmpty()
                    saveButton?.isEnabled = !state.busy && !state.loading
                    saveButton?.text = when {
                        state.busy -> "Guardando…"
                        state.loading -> "Cargando…"
                        model.draft.id != null -> "Guardar cambios"
                        else -> "Guardar movimiento"
                    }
                    setFieldsEnabled(content, !state.busy)
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

    private fun renderForm(content: LinearLayout, state: FinanceState) {
        val context = requireContext()
        val draft = model.draft
        val header = FinanceUi.column(context)
        header.addView(FinanceUi.text(context, if (draft.id == null) "Nuevo movimiento" else "Editar movimiento", 23f, bold = true))
        content.addView(header)

        val toggle = MaterialButtonToggleGroup(context).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = FinanceUi.dp(context, 20) }
        }
        val income = MaterialButton(context).apply { id = View.generateViewId(); text = "Ingreso" }
        val expense = MaterialButton(context).apply { id = View.generateViewId(); text = "Gasto" }
        listOf(income, expense).forEach { button ->
            button.layoutParams = LinearLayout.LayoutParams(0, FinanceUi.dp(context, 48), 1f)
            button.isAllCaps = false
            toggle.addView(button)
        }
        toggle.check(if (draft.type == "income") income.id else expense.id)
        content.addView(toggle)
        currencyText = FinanceUi.text(context, draft.currency ?: state.data.currency, 13f, FinanceUi.muted).apply {
            gravity = Gravity.CENTER
        }
        content.addView(currencyText)
        amountField = FinanceUi.field(context, "Monto", draft.amount,
            InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL).apply {
            editText?.apply {
                textSize = 36f
                gravity = Gravity.CENTER
                setTextColor(if (draft.type == "income") FinanceUi.green else FinanceUi.orange)
                filters = arrayOf(InputFilter.LengthFilter(12))
                doAfterTextChanged { draft.amount = it.toString(); amountField?.error = null }
            }
        }
        content.addView(amountField)
        content.addView(FinanceUi.text(context, "CATEGORÍA", 12f, FinanceUi.muted, true))
        val categories = ChipGroup(context).apply {
            isSingleSelection = true
            isSelectionRequired = true
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = FinanceUi.dp(context, 12) }
        }
        content.addView(categories)
        fun renderCategories() {
            categories.removeAllViews()
            val options = if (draft.type == "income") FinanceUi.incomeCategories else FinanceUi.expenseCategories
            (options + draft.category).distinct().forEach { category ->
                categories.addView(Chip(context).apply {
                    text = category
                    isCheckable = true
                    isChecked = category == draft.category
                    setTextColor(FinanceUi.ink)
                    chipBackgroundColor = ColorStateList.valueOf(if (isChecked) Color.rgb(217, 242, 225) else Color.WHITE)
                    setOnClickListener { draft.category = category; renderCategories() }
                })
            }
            categories.addView(Chip(context).apply {
                text = "+ Categoría"
                setTextColor(FinanceUi.ink)
                chipBackgroundColor = ColorStateList.valueOf(Color.WHITE)
                setOnClickListener {
                    textDialog("Nueva categoría", "Nombre", 30) { value ->
                        draft.category = options.firstOrNull { it.equals(value, true) } ?: value
                        renderCategories()
                    }
                }
            })
        }
        renderCategories()
        toggle.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked) {
                draft.type = if (checkedId == income.id) "income" else "expense"
                draft.category = if (draft.type == "income") "Salario" else "Alimentación"
                amountField?.editText?.setTextColor(if (draft.type == "income") FinanceUi.green else FinanceUi.orange)
                renderCategories()
            }
        }

        content.addView(FinanceUi.text(context, "FECHA", 12f, FinanceUi.muted, true))
        val dateButton = FinanceUi.button(context, FinanceFormat.dateLabel(draft.date)) { }
        dateButton.setOnClickListener {
            val parts = draft.date.split('-').map { it.toInt() }
            DatePickerDialog(context, { _, year, month, day ->
                draft.date = String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month + 1, day)
                dateButton.text = FinanceFormat.dateLabel(draft.date)
            }, parts[0], parts[1] - 1, parts[2]).show()
        }
        content.addView(dateButton)
        content.addView(FinanceUi.text(context, "MÉTODO DE PAGO (OPCIONAL)", 12f, FinanceUi.muted, true))
        val payment = FinanceUi.button(context, draft.paymentMethod.ifBlank { "Sin especificar" }) { }
        payment.setOnClickListener {
            val methods = arrayOf("Sin especificar", "Efectivo", "Tarjeta", "Transferencia", "Otro")
            MaterialAlertDialogBuilder(context).setTitle("Método de pago")
                .setSingleChoiceItems(methods, methods.indexOf(draft.paymentMethod).coerceAtLeast(0)) { dialog, index ->
                    draft.paymentMethod = if (index == 0) "" else methods[index]
                    payment.text = methods[index]
                    dialog.dismiss()
                }.setNegativeButton("Cancelar", null).show()
        }
        content.addView(payment)
        content.addView(FinanceUi.field(context, "Descripción (opcional)", draft.description).apply {
            editText?.filters = arrayOf(InputFilter.LengthFilter(160))
            editText?.doAfterTextChanged { draft.description = it.toString() }
        })
        content.addView(FinanceUi.text(context, "ETIQUETAS", 12f, FinanceUi.muted, true))
        val tags = ChipGroup(context)
        content.addView(tags)
        fun renderTags() {
            tags.removeAllViews()
            draft.tags.forEach { tag ->
                tags.addView(Chip(context).apply {
                    text = "#$tag"
                    isCloseIconVisible = true
                    setTextColor(FinanceUi.ink)
                    chipBackgroundColor = ColorStateList.valueOf(Color.WHITE)
                    setOnCloseIconClickListener { draft.tags = draft.tags - tag; renderTags() }
                })
            }
        }
        renderTags()
        content.addView(FinanceUi.button(context, "+ Agregar etiqueta") {
            if (draft.tags.size >= 8) {
                Snackbar.make(content, "Puedes agregar hasta 8 etiquetas", Snackbar.LENGTH_SHORT).show()
            } else textDialog("Agregar etiqueta", "Etiqueta", 24) { value ->
                val tag = value.removePrefix("#").trim().replace(Regex("\\s+"), "_")
                if (tag.isNotEmpty()) draft.tags = (draft.tags + tag).distinct()
                renderTags()
            }
        })
        saveButton = FinanceUi.button(context, "Guardar movimiento", true) {
            if (FinanceFormat.cents(draft.amount) == null) {
                amountField?.error = "Ingresa un monto mayor que cero, con hasta 2 decimales"
                amountField?.editText?.requestFocus()
            } else {
                WindowCompat.getInsetsController(requireActivity().window, content).hide(WindowInsetsCompat.Type.ime())
                model.saveMovement()
            }
        }
        content.addView(saveButton)
        if (draft.id != null) {
            content.addView(FinanceUi.button(context, "Cancelar edición") { model.showMovement(draft.id!!) })
        } else {
            latestButton = FinanceUi.button(context, "Ver último movimiento") {
                model.state.value.data.movements.firstOrNull()?.let { model.showMovement(it.id) }
            }
            content.addView(latestButton)
        }
    }

    private fun renderDetail(content: LinearLayout, state: FinanceState) {
        val context = requireContext()
        val movement = state.data.movements.firstOrNull { it.id == state.detailId }
        content.addView(FinanceUi.text(context, "Detalle", 23f, bold = true))
        if (movement == null) {
            content.addView(FinanceUi.text(context, "El movimiento ya no está disponible."))
            content.addView(FinanceUi.button(context, "Nuevo movimiento") { model.newMovement() })
            return
        }
        val color = if (movement.type == "income") FinanceUi.green else FinanceUi.orange
        content.addView(FinanceUi.text(context, if (movement.type == "income") "Ingreso" else "Gasto", 14f, FinanceUi.muted).apply { gravity = Gravity.CENTER })
        content.addView(FinanceUi.text(context,
            (if (movement.type == "income") "+" else "−") + FinanceFormat.money(movement.amountCents, movement.currency),
            36f, color, true).apply { gravity = Gravity.CENTER })
        content.addView(FinanceUi.text(context, "${movement.category} · ${FinanceFormat.dateLabel(movement.date)}", 14f, FinanceUi.muted).apply { gravity = Gravity.CENTER })
        val details = FinanceUi.column(context, 18)
        listOf("Descripción" to movement.description.ifBlank { "Sin descripción" },
            "Método de pago" to movement.paymentMethod.ifBlank { "Sin especificar" },
            "Etiquetas" to movement.tags.joinToString(" ") { "#$it" }.ifBlank { "Sin etiquetas" }).forEach { (label, value) ->
            details.addView(FinanceUi.text(context, label, 12f, FinanceUi.muted, true))
            details.addView(FinanceUi.text(context, value, 15f))
        }
        content.addView(FinanceUi.card(context, details))
        content.addView(FinanceUi.button(context, "Editar movimiento") { model.editMovement(movement) })
        content.addView(FinanceUi.button(context, "Eliminar movimiento") { confirmDelete(movement) }.apply { setTextColor(FinanceUi.orange) })
        content.addView(FinanceUi.button(context, "Nuevo movimiento", true) { model.newMovement() })
    }

    private fun confirmDelete(movement: FinancialMovement) {
        MaterialAlertDialogBuilder(requireContext()).setTitle("Eliminar movimiento")
            .setMessage("¿Eliminar este movimiento? También se actualizará el gasto de tus presupuestos.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Eliminar") { _, _ -> model.deleteMovement(movement.id) }.show()
    }

    private fun textDialog(title: String, hint: String, limit: Int, onSave: (String) -> Unit) {
        val field = FinanceUi.field(requireContext(), hint)
        field.editText?.filters = arrayOf(InputFilter.LengthFilter(limit))
        val wrapper = FinanceUi.column(requireContext(), 20).apply { addView(field) }
        val dialog = MaterialAlertDialogBuilder(requireContext()).setTitle(title).setView(wrapper)
            .setNegativeButton("Cancelar", null).setPositiveButton("Agregar", null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = field.editText?.text.toString().trim()
                if (value.isEmpty()) field.error = "Este campo es obligatorio"
                else { onSave(value); dialog.dismiss() }
            }
        }
        dialog.show()
    }

    private fun setFieldsEnabled(view: View, enabled: Boolean) {
        if (view is ViewGroup) for (index in 0 until view.childCount) setFieldsEnabled(view.getChildAt(index), enabled)
        if (view is EditText || view is MaterialButton || view is Chip) {
            view.isEnabled = enabled && (view !== saveButton || !model.state.value.loading)
        }
    }

    override fun onDestroyView() {
        amountField = null
        saveButton = null
        currencyText = null
        latestButton = null
        super.onDestroyView()
    }
}

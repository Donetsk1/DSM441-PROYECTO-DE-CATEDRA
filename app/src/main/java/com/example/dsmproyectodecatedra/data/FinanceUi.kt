package com.example.dsmproyectodecatedra.data

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

object FinanceUi {
    val ink = Color.rgb(13, 23, 39)
    val muted = Color.rgb(83, 105, 116)
    val surface = Color.rgb(243, 248, 246)
    val line = Color.rgb(220, 231, 225)
    val green = Color.rgb(25, 154, 75)
    val orange = Color.rgb(220, 78, 43)
    val yellow = Color.rgb(164, 194, 36)
    val expenseCategories = listOf("Alimentación", "Transporte", "Ocio", "Servicios", "Estudios", "Salud", "Otros")
    val incomeCategories = listOf("Salario", "Beca", "Familia", "Ventas", "Otros")

    fun dp(context: Context, value: Int): Int = (context.resources.displayMetrics.density * value).toInt()

    fun column(context: Context, padding: Int = 0) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(context, padding), dp(context, padding), dp(context, padding), dp(context, padding))
        layoutParams = LinearLayout.LayoutParams(-1, -2)
    }

    fun text(context: Context, value: String, size: Float = 14f, color: Int = ink, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 8) }
    }

    fun card(context: Context, content: View, background: Int = Color.WHITE, border: Int = line) = MaterialCardView(context).apply {
        radius = dp(context, 20).toFloat()
        cardElevation = 0f
        strokeWidth = dp(context, 1)
        strokeColor = border
        setCardBackgroundColor(background)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 12) }
        addView(content)
    }

    fun button(context: Context, label: String, primary: Boolean = false, action: () -> Unit) = MaterialButton(context).apply {
        text = label
        isAllCaps = false
        cornerRadius = dp(context, 16)
        minHeight = dp(context, 48)
        setTextColor(if (primary) Color.WHITE else ink)
        backgroundTintList = ColorStateList.valueOf(if (primary) ink else Color.WHITE)
        strokeWidth = if (primary) 0 else dp(context, 1)
        strokeColor = ColorStateList.valueOf(line)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 8) }
        setOnClickListener { action() }
    }

    fun field(context: Context, label: String, initial: String = "", input: Int = android.text.InputType.TYPE_CLASS_TEXT): TextInputLayout {
        return TextInputLayout(context).apply {
            hint = label
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            setBoxCornerRadii(dp(context, 14).toFloat(), dp(context, 14).toFloat(), dp(context, 14).toFloat(), dp(context, 14).toFloat())
            boxBackgroundColor = Color.WHITE
            defaultHintTextColor = ColorStateList.valueOf(muted)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(context, 14) }
            addView(TextInputEditText(context).apply {
                inputType = input
                setTextColor(ink)
                setText(initial)
                textSize = 15f
                setSingleLine(true)
                layoutParams = LinearLayout.LayoutParams(-1, -2)
            })
        }
    }
}

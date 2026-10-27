package com.example.dsmproyectodecatedra.news

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.example.dsmproyectodecatedra.R
import com.example.dsmproyectodecatedra.data.FinanceUi
import kotlinx.coroutines.launch

class NewsFragment : Fragment(R.layout.fragment_news) {
    private val model: NewsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { state ->
                    render(view.findViewById(R.id.newsContent), state)
                }
            }
        }
    }

    private fun render(content: LinearLayout, state: NewsUiState) {
        content.removeAllViews()
        val context = requireContext()

        content.addView(FinanceUi.text(context, "Noticias UDB", 24f, bold = true))

        when {
            state.loading -> {
                content.addView(FinanceUi.text(context, "Cargando noticias…", color = FinanceUi.muted))
            }
            state.error != null -> {
                content.addView(FinanceUi.text(context, state.error, color = FinanceUi.orange))
                content.addView(FinanceUi.button(context, "Reintentar", true) { model.load() })
            }
            state.articles.isEmpty() -> {
                content.addView(FinanceUi.text(context, "No se encontraron noticias por ahora.", color = FinanceUi.muted))
            }
            else -> {
                state.articles.forEach { article -> content.addView(articleCard(context, article)) }
            }
        }
    }

    private fun articleCard(context: android.content.Context, article: NewsArticle): View {
        val body = FinanceUi.column(context)
        if (article.imageUrl != null) {
            body.addView(ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                layoutParams = LinearLayout.LayoutParams(-1, FinanceUi.dp(context, 160)).apply {
                    bottomMargin = FinanceUi.dp(context, 12)
                }
                load(article.imageUrl)
            })
        }
        body.addView(FinanceUi.text(context, article.title, 16f, bold = true))
        if (article.date.isNotBlank()) {
            body.addView(FinanceUi.text(context, article.date, 12f, FinanceUi.muted))
        }
        body.addView(FinanceUi.button(context, "Ver nota") {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(article.link)))
        })
        return FinanceUi.card(context, body)
    }
}

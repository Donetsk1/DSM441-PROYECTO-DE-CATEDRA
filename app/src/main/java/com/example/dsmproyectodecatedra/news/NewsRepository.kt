package com.example.dsmproyectodecatedra.news

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

private const val NEWS_URL = "https://www.udb.edu.sv/udb/noticias"
private const val TIMEOUT_MS = 15_000

class NewsRepository {
    suspend fun fetchLatest(limit: Int = 5): List<NewsArticle> = withContext(Dispatchers.IO) {
        val document = Jsoup.connect(NEWS_URL)
            .userAgent("Mozilla/5.0")
            .timeout(TIMEOUT_MS)
            .get()

        document.select("article.newnav").take(limit).mapNotNull { article ->
            val titleLink = article.selectFirst(".newnav-body-title-link") ?: return@mapNotNull null
            val title = titleLink.selectFirst("h2")?.text()?.trim().orEmpty()
            val link = titleLink.attr("abs:href")
            if (title.isBlank() || link.isBlank()) return@mapNotNull null

            val date = article.selectFirst(".newnav-body-date p")?.text()?.trim().orEmpty()
            val imageUrl = article.selectFirst(".newnav-head-image img")?.attr("abs:src")?.takeIf { it.isNotBlank() }

            NewsArticle(title, date, imageUrl, link)
        }
    }
}

package com.savantle.backend.services

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.LoadState
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.text.Normalizer

data class ScreenshotResult(
    val savantUrl: String,
    val pngBytes: ByteArray
)

@Service
class ScreenshotService {

    private val log = LoggerFactory.getLogger(ScreenshotService::class.java)

    private var playwright: Playwright? = null
    private var browser: Browser? = null

    @PostConstruct
    fun init() {
        try {
            playwright = Playwright.create()
            browser = playwright!!.chromium().launch(
                BrowserType.LaunchOptions().setHeadless(true)
            )
            log.info("Playwright Chromium initialized")
        } catch (e: Exception) {
            log.error("Failed to initialize Playwright", e)
        }
    }

    @PreDestroy
    fun shutdown() {
        try { browser?.close() } catch (_: Exception) {}
        try { playwright?.close() } catch (_: Exception) {}
    }

    fun capturePercentiles(mlbamId: Int, fullName: String, isPitcher: Boolean): ScreenshotResult? {
        val br = browser ?: run {
            log.warn("Browser not initialized")
            return null
        }
        val slug = toSlug(fullName)
        val type = if (isPitcher) "pitcher" else "batter"
        val url = "https://baseballsavant.mlb.com/savant-player/$slug-$mlbamId?stats=statcast-r-$type"

        val context = br.newContext(
            Browser.NewContextOptions()
                .setViewportSize(1400, 2000)
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36")
        )
        try {
            val page = context.newPage()
            page.navigate(url)
            page.waitForLoadState(LoadState.NETWORKIDLE)

            val selectors = listOf(
                "#percentileRankings",
                "div.percentile-rankings",
                "[id*='percentile']"
            )
            var element = selectors.firstNotNullOfOrNull { sel ->
                runCatching { page.querySelector(sel) }.getOrNull()
            }

            if (element == null) {
                log.warn("No percentile section found for $fullName ($mlbamId) at $url")
                return null
            }

            element.scrollIntoViewIfNeeded()
            page.waitForTimeout(1500.0)

            val bytes = element.screenshot()
            if (bytes == null || bytes.size < 5_000) {
                log.warn("Screenshot too small for $fullName")
                return null
            }
            return ScreenshotResult(savantUrl = url, pngBytes = bytes)
        } catch (e: Exception) {
            log.warn("Screenshot failed for $fullName ($mlbamId): ${e.message}")
            return null
        } finally {
            try { context.close() } catch (_: Exception) {}
        }
    }

    private fun toSlug(name: String): String {
        val nfd = Normalizer.normalize(name, Normalizer.Form.NFD)
        return nfd.replace(Regex("[\\u0300-\\u036f]"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .trim()
            .replace(Regex("\\s+"), "-")
    }
}

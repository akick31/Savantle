package com.savantle.backend.services

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.LoadState
import com.microsoft.playwright.options.WaitForSelectorState
import com.savantle.backend.model.ScreenshotResult
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.text.Normalizer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

@Service
class ScreenshotService {
    companion object {
        private val PERCENTILE_SELECTORS =
            listOf(
                "#percentileRankings",
                "div.percentile-rankings",
                "[id*='percentile']",
                ".stat-percentile-wrapper",
                "#player-percentile",
            )
        private const val PERCENTILE_HEADING_SELECTOR =
            "text=/\\d{4}\\s+MLB\\s+Percentile\\s+Rankings|MLB\\s+Percentile\\s+Rankings/i"

        private val BLOCKED_RESOURCE_TYPES = setOf("media", "websocket")

        // Block third-party domains (ads, analytics, tracking)
        private val BLOCKED_URL_PATTERNS =
            listOf(
                "google-analytics", "googletagmanager", "doubleclick",
                "facebook", "twitter", "amazon-adsystem", "scorecardresearch",
                "omtrdc", "demdex", "krxd", "chartbeat", "quantserve",
                "cdn.cookielaw", "onetrust", "bat.bing",
            )
    }

    private val log = LoggerFactory.getLogger(ScreenshotService::class.java)

    private var playwright: Playwright? = null
    private var browser: Browser? = null

    private val captureLock = ReentrantLock()

    @PostConstruct
    fun init() {
        try {
            playwright = Playwright.create()
            browser =
                playwright!!.chromium().launch(
                    BrowserType.LaunchOptions()
                        .setHeadless(true)
                        .setArgs(
                            listOf(
                                "--no-sandbox",
                                "--disable-dev-shm-usage",
                                "--disable-extensions",
                                "--disable-gpu",
                                "--disable-background-networking",
                                "--disable-default-apps",
                                "--no-first-run",
                                "--mute-audio",
                            ),
                        ),
                )
            log.info("Playwright Chromium initialized (fresh context per capture, serialized)")
        } catch (e: Exception) {
            log.error("Failed to initialize Playwright", e)
        }
    }

    @PreDestroy
    fun shutdown() {
        runCatching { browser?.close() }
        runCatching { playwright?.close() }
    }

    private fun createContext(): BrowserContext {
        val ctx =
            browser!!.newContext(
                Browser.NewContextOptions()
                    .setViewportSize(1280, 1600)
                    .setUserAgent(
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    )
                    .setJavaScriptEnabled(true),
            )
        ctx.route("**/*") { route ->
            val req = route.request()
            val type = req.resourceType()
            val url = req.url()
            if (type in BLOCKED_RESOURCE_TYPES ||
                BLOCKED_URL_PATTERNS.any { url.contains(it, ignoreCase = true) }
            ) {
                route.abort()
            } else {
                route.resume()
            }
        }
        return ctx
    }

    fun capturePercentiles(
        mlbamId: Int,
        fullName: String,
        isPitcher: Boolean,
    ): ScreenshotResult? {
        val slug = toSlug(fullName)
        val type = if (isPitcher) "pitcher" else "batter"
        val url = "https://baseballsavant.mlb.com/savant-player/$slug-$mlbamId?stats=statcast-r-$type"

        return captureLock.withLock {
            if (browser == null) {
                log.warn("Browser not initialized")
                return@withLock null
            }

            val context = createContext()
            try {
                val page = context.newPage()
                try {
                    page.setDefaultNavigationTimeout(45_000.0)
                    page.setDefaultTimeout(30_000.0)

                    page.navigate(
                        url,
                        Page.NavigateOptions()
                            .setWaitUntil(LoadState.DOMCONTENTLOADED)
                            .setTimeout(45_000.0),
                    )

                    val element =
                        findPercentileContainerByHeading(page, fullName, mlbamId)
                            ?: findPercentileContainerByFallbackSelectors(page, fullName, mlbamId)
                            ?: run {
                                log.warn("No percentile section found for $fullName ($mlbamId) - skipping")
                                return@withLock null
                            }

                    element.scrollIntoViewIfNeeded()

                    runCatching {
                        page.waitForFunction(
                            "(el) => el.querySelector('canvas, svg, .bar, [class*=\"bar\"]') !== null",
                            element,
                            Page.WaitForFunctionOptions().setTimeout(5_000.0),
                        )
                    }
                    page.waitForTimeout(500.0)

                    val bytes = element.screenshot()
                    if (bytes == null || bytes.size < 2_000) {
                        log.warn("Screenshot too small (${bytes?.size ?: 0}B) for $fullName - skipping")
                        return@withLock null
                    }
                    log.info("Captured ${bytes.size / 1024}KB screenshot for $fullName ($mlbamId)")
                    ScreenshotResult(savantUrl = url, pngBytes = bytes)
                } catch (e: Exception) {
                    log.warn("Screenshot failed for $fullName ($mlbamId): ${e.message}")
                    null
                } finally {
                    runCatching { page.close() }
                }
            } finally {
                runCatching { context.close() }
            }
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

    private fun findPercentileContainerByHeading(
        page: Page,
        fullName: String,
        mlbamId: Int,
    ) = runCatching {
        page.waitForSelector(
            PERCENTILE_HEADING_SELECTOR,
            Page.WaitForSelectorOptions()
                .setState(WaitForSelectorState.ATTACHED)
                .setTimeout(20_000.0),
        )

        val heading = page.querySelector(PERCENTILE_HEADING_SELECTOR) ?: return@runCatching null
        val containerHandle =
            heading.evaluateHandle(
                """
                (el) => {
                  const specific = el.closest('#percentileRankings, .percentile-rankings, .stat-percentile-wrapper, #player-percentile');
                  if (specific) return specific;
                  const generic = el.closest('[id*="percentile" i], [class*="percentile" i], [class*="ranking" i]');
                  if (generic) return generic;
                  return el.parentElement;
                }
                """.trimIndent(),
            )
        val container = containerHandle.asElement()
        if (container == null) {
            log.warn("Found percentile heading but no container for $fullName ($mlbamId)")
        }
        container
    }.getOrElse {
        log.warn("Heading-based percentile lookup failed for $fullName ($mlbamId): ${it.message}")
        null
    }

    private fun findPercentileContainerByFallbackSelectors(
        page: Page,
        fullName: String,
        mlbamId: Int,
    ) = PERCENTILE_SELECTORS.firstNotNullOfOrNull { sel ->
        runCatching {
            page.waitForSelector(
                sel,
                Page.WaitForSelectorOptions()
                    .setState(WaitForSelectorState.ATTACHED)
                    .setTimeout(5_000.0),
            )
            page.querySelector(sel)
        }.getOrElse { null }
    }.also {
        if (it == null) log.warn("Fallback percentile selectors failed for $fullName ($mlbamId)")
    }
}

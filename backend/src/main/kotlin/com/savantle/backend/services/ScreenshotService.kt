package com.savantle.backend.services

import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.WaitForSelectorState
import com.microsoft.playwright.options.WaitUntilState
import com.savantle.backend.model.ScreenshotResult
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.text.Normalizer
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.random.Random

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

        private val BLOCKED_URL_PATTERNS =
            listOf(
                "google-analytics", "googletagmanager", "doubleclick",
                "facebook", "twitter", "amazon-adsystem", "scorecardresearch",
                "omtrdc", "demdex", "krxd", "chartbeat", "quantserve",
                "cdn.cookielaw", "onetrust", "bat.bing",
            )

        private const val POLITE_DELAY_MIN_MS = 1_500L
        private const val POLITE_DELAY_MAX_MS = 3_000L

        /**
         * Identifies this bot to Baseball Savant admins. The sandbox must remain enabled so that
         * Chromium's process isolation protects the host — Docker containers need --cap-add=SYS_ADMIN
         * (or a permissive seccomp profile) for the Linux user-namespace sandbox to function.
         */
        private const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36 BoxScoreBot/1.0; +contact@savantle.com"
    }

    private val log = LoggerFactory.getLogger(ScreenshotService::class.java)

    private var playwright: Playwright? = null
    private var browser: Browser? = null

    /** Route/context handling is not thread-safe across a shared browser instance. */
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
            log.info("Playwright Chromium initialized (sandbox enabled, fresh context per capture)")
        } catch (e: Exception) {
            log.error("Failed to initialize Playwright", e)
        }
    }

    @PreDestroy
    fun shutdown() {
        log.info("Shutting down Playwright")
        captureLock.withLock {
            runCatching { browser?.close() }.onFailure { log.warn("Browser close error: ${it.message}") }
            runCatching { playwright?.close() }.onFailure { log.warn("Playwright close error: ${it.message}") }
            browser = null
            playwright = null
        }
    }

    @Cacheable(
        cacheNames = ["percentile-screenshots"],
        key = "#mlbamId + ':' + T(java.time.LocalDate).now().toString()",
        unless = "#result == null",
    )
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

            Thread.sleep(Random.nextLong(POLITE_DELAY_MIN_MS, POLITE_DELAY_MAX_MS))

            val context = createContext()
            try {
                val page = context.newPage()
                try {
                    page.setDefaultNavigationTimeout(32_000.0)
                    page.setDefaultTimeout(22_000.0)

                    page.navigate(
                        url,
                        Page.NavigateOptions()
                            .setWaitUntil(WaitUntilState.DOMCONTENTLOADED)
                            .setTimeout(32_000.0),
                    )

                    val element =
                        findPercentileContainerByHeading(page, fullName, mlbamId)
                            ?: findPercentileContainerByFallbackSelectors(page, fullName, mlbamId)

                    if (element == null) {
                        log.warn("No percentile section found for $fullName ($mlbamId) - skipping")
                        return@withLock null
                    }

                    element.scrollIntoViewIfNeeded()

                    runCatching {
                        page.waitForFunction(
                            "(el) => el.querySelector('canvas, svg, .bar, [class*=\"bar\"]') !== null",
                            element,
                            Page.WaitForFunctionOptions().setTimeout(3_500.0),
                        )
                    }
                    page.waitForTimeout(300.0)

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

    private fun createContext(): BrowserContext {
        val ctx =
            browser!!.newContext(
                Browser.NewContextOptions()
                    .setViewportSize(1280, 1600)
                    .setUserAgent(USER_AGENT)
                    .setJavaScriptEnabled(true),
            )
        ctx.route("**/*") { route ->
            val req = runCatching { route.request() }.getOrNull()
            if (req == null) {
                runCatching { route.resume() }
                return@route
            }
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
                .setTimeout(14_000.0),
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
                    .setTimeout(4_000.0),
            )
            page.querySelector(sel)
        }.getOrElse { null }
    }.also {
        if (it == null) log.warn("Fallback percentile selectors failed for $fullName ($mlbamId)")
    }
}

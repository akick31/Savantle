package com.savantle.backend.services

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.LocalDate

data class StatDefinition(
    val csvColumns: List<String>,
    val displayName: String,
    val higherIsBetter: Boolean
)

data class PlayerPercentiles(
    val mlbamId: Int,
    val isBatter: Boolean,
    val stats: Map<String, Int>
)

@Service
class BaseballSavantService {

    private val log = LoggerFactory.getLogger(BaseballSavantService::class.java)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    val batterStatDefs = listOf(
        StatDefinition(listOf("exit_velocity_avg"), "Exit Velo", true),
        StatDefinition(listOf("hard_hit_percent"), "Hard Hit%", true),
        StatDefinition(listOf("barrel_batted_rate", "barrel_percent"), "Barrel%", true),
        StatDefinition(listOf("xba"), "xBA", true),
        StatDefinition(listOf("xslg"), "xSLG", true),
        StatDefinition(listOf("xwoba"), "xwOBA", true),
        StatDefinition(listOf("k_percent"), "K%", false),
        StatDefinition(listOf("bb_percent"), "BB%", true),
        StatDefinition(listOf("whiff_percent"), "Whiff%", false),
        StatDefinition(listOf("chase_rate", "oz_swing_percent"), "Chase Rate", false),
        StatDefinition(listOf("sprint_speed"), "Sprint Speed", true),
    )

    val pitcherStatDefs = listOf(
        StatDefinition(listOf("exit_velocity_avg", "p_exit_velocity_avg"), "Exit Velo", false),
        StatDefinition(listOf("hard_hit_percent", "p_hard_hit_percent"), "Hard Hit%", false),
        StatDefinition(listOf("barrel_batted_rate", "barrel_percent", "p_barrel_batted_rate"), "Barrel%", false),
        StatDefinition(listOf("xba", "p_xba"), "xBA", false),
        StatDefinition(listOf("xslg", "p_xslg"), "xSLG", false),
        StatDefinition(listOf("xwoba", "p_xwoba"), "xwOBA", false),
        StatDefinition(listOf("k_percent", "p_k_percent"), "K%", true),
        StatDefinition(listOf("bb_percent", "p_bb_percent"), "BB%", false),
        StatDefinition(listOf("whiff_percent", "p_whiff_percent"), "Whiff%", true),
        StatDefinition(listOf("p_called_strike_rate", "called_strike_rate", "oz_swing_percent", "chase_rate"), "Chase Rate", true),
    )

    fun fetchPercentiles(year: Int = LocalDate.now().year): Map<Int, PlayerPercentiles> {
        val result = mutableMapOf<Int, PlayerPercentiles>()

        try {
            val rows = fetchCSV("batter", year)
            log.info("Fetched ${rows.size} batter rows for $year")
            if (rows.isNotEmpty()) {
                result.putAll(computePercentiles(rows, batterStatDefs, isBatter = true))
            }
        } catch (e: Exception) {
            log.error("Failed to fetch batter statcast data: ${e.message}")
        }

        try {
            val rows = fetchCSV("pitcher", year)
            log.info("Fetched ${rows.size} pitcher rows for $year")
            if (rows.isNotEmpty()) {
                result.putAll(computePercentiles(rows, pitcherStatDefs, isBatter = false))
            }
        } catch (e: Exception) {
            log.error("Failed to fetch pitcher statcast data: ${e.message}")
        }

        if (result.isEmpty() && year == LocalDate.now().year) {
            log.warn("No statcast data found for $year, falling back to ${year - 1}")
            return fetchPercentiles(year - 1)
        }

        return result
    }

    private fun fetchCSV(type: String, year: Int): List<Map<String, String>> {
        // Try qualified players first, fall back to lower threshold
        for (min in listOf("q", "50", "25", "1")) {
            val url = "https://baseballsavant.mlb.com/leaderboard/statcast?type=$type&year=$year&position=&team=&min=$min&csv=true"
            log.info("Fetching $type statcast CSV: $url")
            try {
                val request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                    .header("Accept", "text/csv,text/plain,*/*")
                    .GET()
                    .build()
                val body = client.send(request, HttpResponse.BodyHandlers.ofString()).body()
                val rows = parseCSV(body)
                if (rows.size >= 30) {
                    log.info("Got ${rows.size} $type rows with min=$min")
                    return rows
                }
                log.warn("Only ${rows.size} rows with min=$min, trying lower threshold")
            } catch (e: Exception) {
                log.warn("Failed to fetch with min=$min: ${e.message}")
            }
        }
        return emptyList()
    }

    private fun parseCSV(csv: String): List<Map<String, String>> {
        val lines = csv.trim().split("\n").filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()

        // Log headers to help debug column name issues
        val headers = parseCsvLine(lines[0]).map { it.trim().lowercase().replace("\"", "") }
        log.debug("CSV headers: ${headers.take(20).joinToString()}")

        return lines.drop(1).mapNotNull { line ->
            val values = parseCsvLine(line)
            if (values.size >= headers.size) {
                headers.zip(values.map { it.trim().replace("\"", "") }).toMap()
            } else null
        }
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuotes = false
        val current = StringBuilder()
        for (c in line) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> { result.add(current.toString()); current.clear() }
                else -> current.append(c)
            }
        }
        result.add(current.toString())
        return result
    }

    private fun computePercentiles(
        rows: List<Map<String, String>>,
        statDefs: List<StatDefinition>,
        isBatter: Boolean
    ): Map<Int, PlayerPercentiles> {
        val validStatDefs = statDefs.filter { stat ->
            rows.any { row -> stat.csvColumns.any { col -> !row[col].isNullOrBlank() && row[col] != "null" && row[col] != "NA" } }
        }
        log.info("Valid stats (${if (isBatter) "batter" else "pitcher"}): ${validStatDefs.map { it.displayName }}")

        val statValues = validStatDefs.associate { stat ->
            val values = rows.mapNotNull { row ->
                val raw = stat.csvColumns.firstNotNullOfOrNull { col ->
                    row[col]?.takeIf { it.isNotBlank() && it != "null" && it != "NA" }
                }
                raw?.toDoubleOrNull()
            }.sorted()
            stat.displayName to values
        }

        return rows.mapNotNull { row ->
            val id = (row["player_id"] ?: row["mlbam_id"])?.trim()?.toIntOrNull() ?: return@mapNotNull null

            val percMap = mutableMapOf<String, Int>()
            for (stat in validStatDefs) {
                val raw = stat.csvColumns.firstNotNullOfOrNull { col ->
                    row[col]?.takeIf { it.isNotBlank() && it != "null" && it != "NA" }
                }?.toDoubleOrNull() ?: continue

                val allVals = statValues[stat.displayName] ?: continue
                if (allVals.isEmpty()) continue

                percMap[stat.displayName] = computePercentileRank(allVals, raw, stat.higherIsBetter)
            }

            if (percMap.size < 4) return@mapNotNull null

            id to PlayerPercentiles(mlbamId = id, isBatter = isBatter, stats = percMap)
        }.toMap()
    }

    private fun computePercentileRank(sorted: List<Double>, value: Double, higherIsBetter: Boolean): Int {
        val below = sorted.count { it < value }
        val equal = sorted.count { it == value }
        val rank = ((below + equal.toDouble() / 2) / sorted.size * 100).toInt().coerceIn(1, 99)
        return if (higherIsBetter) rank else 100 - rank
    }
}

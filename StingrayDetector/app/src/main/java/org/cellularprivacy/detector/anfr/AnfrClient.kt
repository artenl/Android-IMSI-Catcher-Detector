package org.cellularprivacy.detector.anfr

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cellularprivacy.detector.data.AnfrSiteEntity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches official transmitter sites from the ANFR open-data API
 * (dataset observatoire_2g_3g_4g, OpenDataSoft v1). Emitter rows are grouped by
 * ANFR station id into one site each, with the union of operators and
 * generations. Network is used only here, on an explicit user action.
 */
object AnfrClient {

    private const val BASE =
        "https://data.anfr.fr/d4c/api/records/1.0/search/?dataset=observatoire_2g_3g_4g"
    private const val PAGE = 1000
    private const val MAX_START = 9000 // ODS v1 caps start+rows at 10000

    private class Acc(val lat: Double, val lon: Double) {
        val ops = sortedSetOf<String>()
        val gens = sortedSetOf<String>()
    }

    data class Result(val sites: List<AnfrSiteEntity>, val truncated: Boolean, val error: String?)

    suspend fun fetchAround(
        lat: Double, lon: Double, radiusM: Int, city: String
    ): Result = withContext(Dispatchers.IO) {
        val acc = HashMap<String, Acc>()
        var start = 0
        var total = Int.MAX_VALUE
        var truncated = false
        try {
            while (start < total && start <= MAX_START) {
                val url = "$BASE&geofilter.distance=$lat,$lon,$radiusM&rows=$PAGE&start=$start" +
                    "&refine.statut=" + URLEncoder.encode("En service", "UTF-8")
                val body = httpGet(url) ?: break
                val obj = JSONObject(body)
                total = obj.optInt("nhits", 0)
                val recs = obj.optJSONArray("records") ?: break
                if (recs.length() == 0) break
                for (i in 0 until recs.length()) {
                    val f = recs.getJSONObject(i).optJSONObject("fields") ?: continue
                    val sta = f.optString("sta_nm_anfr")
                    if (sta.isBlank()) continue
                    val coord = f.optString("coordonnees").split(",")
                    if (coord.size < 2) continue
                    val la = coord[0].trim().toDoubleOrNull() ?: continue
                    val lo = coord[1].trim().toDoubleOrNull() ?: continue
                    val a = acc.getOrPut(sta) { Acc(la, lo) }
                    f.optString("adm_lb_nom").takeIf { it.isNotBlank() }?.let { a.ops.add(it) }
                    f.optString("generation").takeIf { it.isNotBlank() }?.let { a.gens.add(it) }
                }
                start += PAGE
            }
            if (total > MAX_START + PAGE) truncated = true
            val now = System.currentTimeMillis()
            val sites = acc.map { (sta, a) ->
                AnfrSiteEntity(
                    staId = sta, lat = a.lat, lon = a.lon,
                    operators = a.ops.joinToString(","),
                    generations = a.gens.joinToString(","),
                    city = city, timestampMs = now
                )
            }
            Result(sites, truncated, null)
        } catch (t: Throwable) {
            Result(emptyList(), false, t.message ?: t.javaClass.simpleName)
        }
    }

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            setRequestProperty("User-Agent", "StingrayFuzz")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode != 200) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

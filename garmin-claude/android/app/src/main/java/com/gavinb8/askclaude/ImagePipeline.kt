package com.gavinb8.askclaude

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.json.JSONObject
import kotlin.math.pow

/**
 * Claude is a text model -- it cannot generate photorealistic images or
 * arbitrary bitmaps (see docs/limitations.md). What we *can* do, and what
 * this implements, is:
 *
 *   1. Ask Claude for a small structured "diagram spec" (boxes + labelled
 *      arrows) instead of prose, when the user's prompt looks like a
 *      request for a picture/diagram.
 *   2. Render that spec locally on the phone with a plain Canvas.
 *   3. Downscale to a size that's safe for the watch and reduce it to a
 *      handful of colors (a fixed 4-color palette).
 *   4. Hand the result to ClaudeBridgeService to stream to the watch.
 *
 * This is the "closest viable alternative" to literally rendering
 * Claude-generated images on the Venu 3.
 */
object ImagePipeline {

    private val IMAGE_KEYWORDS = listOf(
        "diagram", "draw ", "picture of", "image of", "sketch", "chart of", "illustrate"
    )

    // Kept intentionally small -- every extra pixel is another element in
    // the AppMessage payload once it's chunked into rows.
    const val TARGET_SIZE = 180

    private val PALETTE = intArrayOf(
        Color.rgb(0, 0, 0),
        Color.rgb(255, 255, 255),
        Color.rgb(122, 130, 240),
        Color.rgb(128, 128, 128)
    )

    fun looksLikeImageRequest(prompt: String): Boolean {
        val lower = prompt.lowercase()
        return IMAGE_KEYWORDS.any { lower.contains(it) }
    }

    /**
     * Appended to the user's prompt (not a separate API call) so Claude
     * returns a one-line caption plus a compact JSON diagram spec we can
     * render deterministically. If Claude ignores the instructions or the
     * JSON doesn't parse, callers should fall back to plain text.
     */
    fun diagramInstructionSuffix(): String = """

        Respond in exactly this format: a one-sentence plain-text caption on
        the first line, then on the following lines ONLY a compact JSON
        object (no markdown fences) describing a simple box-and-arrow
        diagram, using a 0-200 by 0-200 coordinate space, matching this
        shape:
        {"nodes":[{"id":"a","label":"Input","x":10,"y":10,"w":70,"h":28}],
         "edges":[{"from":"a","to":"b"}]}
        Keep it to at most 8 nodes and short labels (<= 12 characters).
    """.trimIndent()

    data class RenderedImage(
        val caption: String,
        val width: Int,
        val height: Int,
        val palette: IntArray,
        val rows: Array<IntArray> // palette index per pixel, one IntArray per row
    )

    fun tryParseAndRender(rawResponse: String): RenderedImage? {
        val firstNewline = rawResponse.indexOf('\n')
        if (firstNewline < 0) {
            return null
        }
        val caption = rawResponse.substring(0, firstNewline).trim()
        val jsonPart = rawResponse.substring(firstNewline + 1).trim()
        val jsonStart = jsonPart.indexOf('{')
        if (jsonStart < 0) {
            return null
        }

        val spec = runCatching { JSONObject(jsonPart.substring(jsonStart)) }.getOrNull() ?: return null
        val nodes = spec.optJSONArray("nodes") ?: return null

        val bitmap = Bitmap.createBitmap(TARGET_SIZE, TARGET_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)

        val boxPaint = Paint().apply { color = Color.rgb(122, 130, 240); style = Paint.Style.STROKE; strokeWidth = 3f }
        val textPaint = Paint().apply { color = Color.WHITE; textSize = 14f; isAntiAlias = true }
        val linePaint = Paint().apply { color = Color.GRAY; strokeWidth = 2f }

        val scale = TARGET_SIZE / 200f
        val centers = HashMap<String, Pair<Float, Float>>()

        for (i in 0 until nodes.length()) {
            val node = nodes.getJSONObject(i)
            val x = node.optDouble("x", 10.0).toFloat() * scale
            val y = node.optDouble("y", 10.0).toFloat() * scale
            val w = node.optDouble("w", 60.0).toFloat() * scale
            val h = node.optDouble("h", 24.0).toFloat() * scale
            canvas.drawRect(x, y, x + w, y + h, boxPaint)
            val label = node.optString("label", "").take(12)
            canvas.drawText(label, x + 4, y + h / 2 + 5, textPaint)
            centers[node.optString("id")] = Pair(x + w / 2, y + h / 2)
        }

        val edges = spec.optJSONArray("edges")
        if (edges != null) {
            for (i in 0 until edges.length()) {
                val edge = edges.getJSONObject(i)
                val from = centers[edge.optString("from")]
                val to = centers[edge.optString("to")]
                if (from != null && to != null) {
                    canvas.drawLine(from.first, from.second, to.first, to.second, linePaint)
                }
            }
        }

        val rows = Array(TARGET_SIZE) { y ->
            IntArray(TARGET_SIZE) { x -> nearestPaletteIndex(bitmap.getPixel(x, y)) }
        }

        return RenderedImage(caption, TARGET_SIZE, TARGET_SIZE, PALETTE, rows)
    }

    private fun nearestPaletteIndex(pixel: Int): Int {
        val r = Color.red(pixel)
        val g = Color.green(pixel)
        val b = Color.blue(pixel)
        var best = 0
        var bestDist = Double.MAX_VALUE
        for (i in PALETTE.indices) {
            val pr = Color.red(PALETTE[i])
            val pg = Color.green(PALETTE[i])
            val pb = Color.blue(PALETTE[i])
            val dist = (pr - r).toDouble().pow(2) + (pg - g).toDouble().pow(2) + (pb - b).toDouble().pow(2)
            if (dist < bestDist) {
                bestDist = dist
                best = i
            }
        }
        return best
    }

    fun protocolMessages(requestId: Int, image: RenderedImage): List<Map<String, Any>> {
        val messages = mutableListOf<Map<String, Any>>()
        messages.add(
            mapOf(
                Protocol.KEY_TYPE to Protocol.TYPE_IMAGE_START,
                Protocol.KEY_ID to requestId,
                Protocol.KEY_WIDTH to image.width,
                Protocol.KEY_HEIGHT to image.height,
                Protocol.KEY_PALETTE to image.palette.toList()
            )
        )
        for (y in 0 until image.height) {
            messages.add(
                mapOf(
                    Protocol.KEY_TYPE to Protocol.TYPE_IMAGE_ROW,
                    Protocol.KEY_ID to requestId,
                    Protocol.KEY_ROW to y,
                    Protocol.KEY_PIXELS to image.rows[y].toList()
                )
            )
        }
        messages.add(mapOf(Protocol.KEY_TYPE to Protocol.TYPE_IMAGE_END, Protocol.KEY_ID to requestId))
        return messages
    }
}

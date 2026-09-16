package com.gavinb8.askclaude

/**
 * Splits a Claude text response into AppMessage-sized chunks. BLE AppMessage
 * transfer is slow and has an undocumented (but community-reported) upper
 * bound around 10-16KB per payload, so we stay comfortably small per message
 * and let the watch (ClaudeBridge.mc) reassemble them in order.
 */
object ChunkCodec {

    fun chunksFor(requestId: Int, text: String): List<Map<String, Any>> {
        val safeText = text.ifBlank { "(empty response)" }
        val pieces = safeText.chunked(Protocol.MAX_CHUNK_CHARS)
        return pieces.mapIndexed { index, piece ->
            mapOf(
                Protocol.KEY_TYPE to Protocol.TYPE_CHUNK,
                Protocol.KEY_ID to requestId,
                Protocol.KEY_SEQ to index,
                Protocol.KEY_TOTAL to pieces.size,
                Protocol.KEY_TEXT to piece
            )
        }
    }

    fun errorMessage(requestId: Int, message: String): Map<String, Any> = mapOf(
        Protocol.KEY_TYPE to Protocol.TYPE_ERROR,
        Protocol.KEY_ID to requestId,
        Protocol.KEY_MESSAGE to message
    )
}

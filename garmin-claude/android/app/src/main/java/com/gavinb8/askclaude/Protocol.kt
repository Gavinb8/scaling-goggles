package com.gavinb8.askclaude

/**
 * Wire-format constants shared with /watch/source/Protocol.mc. Keep the two
 * files in sync -- Connect IQ AppMessage payloads are just Dictionaries/Maps
 * with no schema enforcement, so a typo here silently breaks the watch.
 */
object Protocol {
    const val TYPE_PROMPT = "prompt"
    const val TYPE_CANCEL = "cancel"
    const val TYPE_CHUNK = "chunk"
    const val TYPE_ERROR = "error"
    const val TYPE_IMAGE_START = "image_start"
    const val TYPE_IMAGE_ROW = "image_row"
    const val TYPE_IMAGE_END = "image_end"

    const val KEY_TYPE = "type"
    const val KEY_ID = "id"
    const val KEY_CONV = "conv"
    const val KEY_TEXT = "text"
    const val KEY_FOLLOWUP = "followUp"
    const val KEY_SEQ = "seq"
    const val KEY_TOTAL = "total"
    const val KEY_MESSAGE = "message"
    const val KEY_WIDTH = "w"
    const val KEY_HEIGHT = "h"
    const val KEY_PALETTE = "palette"
    const val KEY_ROW = "row"
    const val KEY_PIXELS = "pixels"

    // Must match Protocol.MAX_CHUNK_CHARS on the watch.
    const val MAX_CHUNK_CHARS = 400
}

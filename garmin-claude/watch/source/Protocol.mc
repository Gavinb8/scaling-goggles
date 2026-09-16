using Toybox.Lang;

//
// Shared wire-format constants for the watch <-> phone AppMessage protocol.
// The Android companion app (see /android) must agree with these exact
// string keys since Connect IQ AppMessage payloads are just Dictionaries.
//
module Protocol {

    // Message "type" values
    const TYPE_PROMPT        = "prompt";        // watch -> phone
    const TYPE_CANCEL        = "cancel";        // watch -> phone
    const TYPE_CHUNK         = "chunk";         // phone -> watch (text)
    const TYPE_ERROR         = "error";         // phone -> watch
    const TYPE_IMAGE_START   = "image_start";   // phone -> watch
    const TYPE_IMAGE_ROW     = "image_row";     // phone -> watch
    const TYPE_IMAGE_END     = "image_end";     // phone -> watch

    // Dictionary keys
    const KEY_TYPE      = "type";
    const KEY_ID         = "id";
    const KEY_CONV       = "conv";
    const KEY_TEXT       = "text";
    const KEY_FOLLOWUP   = "followUp";
    const KEY_SEQ        = "seq";
    const KEY_TOTAL      = "total";
    const KEY_MESSAGE    = "message";
    const KEY_WIDTH      = "w";
    const KEY_HEIGHT     = "h";
    const KEY_PALETTE    = "palette";
    const KEY_ROW        = "row";
    const KEY_PIXELS     = "pixels";

    // A single AppMessage transmit() over BLE is slow (roughly 0.5-1KB/sec
    // in practice) and undocumented limits have been reported around
    // 10-16KB per payload. We stay well under that and chunk anything
    // longer than this many characters.
    const MAX_CHUNK_CHARS = 400;

    // Give up waiting for a phone response after this many milliseconds.
    const RESPONSE_TIMEOUT_MS = 45000;
}

using Toybox.Communications;
using Toybox.Application.Storage;
using Toybox.Lang;
using Toybox.System;
using Toybox.WatchUi;

//
// Central place for all watch <-> phone traffic. A single AppMessage
// listener is registered here (Communications only supports one active
// listener at a time system-wide) and re-dispatched to whichever screen
// is currently interested via a small pub/sub callback slot.
//
// Reassembly note: Communications.transmit()/sendMessage() on the phone
// side can only carry one Dictionary at a time and BLE throughput is slow,
// so long Claude responses arrive as a sequence of "chunk" messages that
// get stitched back together here before the UI ever sees them.
//
module ClaudeBridge {

    var listener = null;          // Method reference: invoke(eventType as String, data)
    var activeRequestId = null;   // Number, the request we're currently waiting on
    var conversationId = null;    // String, persists across follow-up questions

    var _inflightParts = {};      // requestId -> Array of chunk strings (index == seq)
    var _inflightTotal = {};      // requestId -> expected chunk count
    var _imageMeta = null;        // {w, h, palette} while an image transfer is in progress
    var _imageRows = null;        // Array of Array<Number> palette indices, one per row

    var _nextId = 0;

    // Registration itself happens on the Application instance (AskClaudeApp)
    // since method() callbacks need a stable object receiver; it forwards
    // straight into onPhoneMessage() below.
    function init() {
        var storedId = Storage.getValue("nextRequestId");
        if (storedId != null) {
            _nextId = storedId;
        }
        var storedConv = Storage.getValue("conversationId");
        if (storedConv != null) {
            conversationId = storedConv;
        }
    }

    // Registers the currently visible screen to receive bridge events.
    // eventType is one of: "chunk_progress", "response", "error",
    // "image_start", "image_progress", "image_ready".
    function setListener(cb) {
        listener = cb;
    }

    function clearListener(cb) {
        if (listener != null && listener.equals(cb)) {
            listener = null;
        }
    }

    function startNewConversation() {
        conversationId = System.getTimer().toString() + "-" + _nextId.toString();
        Storage.setValue("conversationId", conversationId);
    }

    function sendPrompt(promptText, isFollowUp) {
        if (conversationId == null || !isFollowUp) {
            startNewConversation();
        }

        _nextId += 1;
        Storage.setValue("nextRequestId", _nextId);
        activeRequestId = _nextId;

        _inflightParts.put(activeRequestId, []);
        _inflightTotal.remove(activeRequestId);

        var payload = {
            Protocol.KEY_TYPE => Protocol.TYPE_PROMPT,
            Protocol.KEY_ID => activeRequestId,
            Protocol.KEY_CONV => conversationId,
            Protocol.KEY_TEXT => promptText,
            Protocol.KEY_FOLLOWUP => isFollowUp
        };

        Communications.transmit(payload, null, new TransmitResultListener());
        return activeRequestId;
    }

    function cancelActive() {
        if (activeRequestId != null) {
            Communications.transmit(
                { Protocol.KEY_TYPE => Protocol.TYPE_CANCEL, Protocol.KEY_ID => activeRequestId },
                null,
                new TransmitResultListener()
            );
        }
    }

    function onPhoneMessage(msg) {
        var data = msg.data;
        if (data == null || !(data instanceof Lang.Dictionary)) {
            return;
        }

        var type = data.get(Protocol.KEY_TYPE);
        var id = data.get(Protocol.KEY_ID);

        // Ignore stray replies for a request we're no longer tracking
        // (e.g. the user backed out and started a new question).
        if (id != null && activeRequestId != null && id != activeRequestId) {
            return;
        }

        if (type == null) {
            return;
        }

        if (type.equals(Protocol.TYPE_CHUNK)) {
            _handleChunk(id, data);
        } else if (type.equals(Protocol.TYPE_ERROR)) {
            _emit("error", data.get(Protocol.KEY_MESSAGE));
        } else if (type.equals(Protocol.TYPE_IMAGE_START)) {
            _handleImageStart(data);
        } else if (type.equals(Protocol.TYPE_IMAGE_ROW)) {
            _handleImageRow(data);
        } else if (type.equals(Protocol.TYPE_IMAGE_END)) {
            _handleImageEnd(data);
        }
    }

    function _handleChunk(id, data) {
        var seq = data.get(Protocol.KEY_SEQ);
        var total = data.get(Protocol.KEY_TOTAL);
        var text = data.get(Protocol.KEY_TEXT);

        var parts = _inflightParts.get(id);
        if (parts == null) {
            parts = [];
            _inflightParts.put(id, parts);
        }
        while (parts.size() <= seq) {
            parts.add("");
        }
        parts[seq] = text;
        _inflightTotal.put(id, total);

        _emit("chunk_progress", { "seq" => seq, "total" => total });

        if (parts.size() >= total) {
            var full = "";
            for (var i = 0; i < total; i += 1) {
                full += parts[i];
            }
            _inflightParts.remove(id);
            _inflightTotal.remove(id);
            _emit("response", full);
        }
    }

    function _handleImageStart(data) {
        var w = data.get(Protocol.KEY_WIDTH);
        var h = data.get(Protocol.KEY_HEIGHT);
        var palette = data.get(Protocol.KEY_PALETTE);
        _imageMeta = { "w" => w, "h" => h, "palette" => palette };
        _imageRows = new [h];
        _emit("image_start", _imageMeta);
    }

    function _handleImageRow(data) {
        if (_imageRows == null) {
            return;
        }
        var row = data.get(Protocol.KEY_ROW);
        var pixels = data.get(Protocol.KEY_PIXELS);
        if (row != null && row < _imageRows.size()) {
            _imageRows[row] = pixels;
            _emit("image_progress", { "row" => row, "of" => _imageRows.size() });
        }
    }

    function _handleImageEnd(data) {
        if (_imageMeta == null || _imageRows == null) {
            return;
        }
        var result = { "meta" => _imageMeta, "rows" => _imageRows };
        _imageMeta = null;
        _imageRows = null;
        _emit("image_ready", result);
    }

    function _emit(eventType, payload) {
        if (listener != null) {
            listener.invoke(eventType, payload);
        }
    }

    // AppMessage delivery confirmation is best-effort; failures surface to
    // whichever view is listening so it can show a retry/error state.
    class TransmitResultListener extends Communications.ConnectionListener {
        function initialize() {
            Communications.ConnectionListener.initialize();
        }

        function onComplete() {
        }

        function onError() {
            ClaudeBridge._emit("error", "Couldn't reach your phone. Is Garmin Connect running and the watch connected?");
        }
    }
}

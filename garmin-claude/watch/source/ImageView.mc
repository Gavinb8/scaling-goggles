using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Lang;

//
// Phase 5 prototype: displays a small, palette-reduced diagram rendered by
// the phone (see android/.../ImagePipeline.kt) and streamed over AppMessage
// one row at a time. This is the "guaranteed to work" fallback path -- see
// docs/limitations.md for why we don't stream full-resolution photos, and
// for the (untested on real hardware) Communications.makeImageRequest
// alternative worth trying next.
//
class ImageView extends WatchUi.View {

    var _meta;
    var _rowsReceived;
    var _totalRows;
    var _bitmap;

    function initialize() {
        View.initialize();
        _meta = null;
        _rowsReceived = 0;
        _totalRows = 0;
        _bitmap = null;
    }

    function onShow() {
        ClaudeBridge.setListener(method(:onBridgeEvent));
    }

    function onHide() {
        ClaudeBridge.clearListener(method(:onBridgeEvent));
    }

    function onUpdate(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();

        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        dc.setColor(Graphics.COLOR_BLUE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, 14, Graphics.FONT_TINY, "CLAUDE", Graphics.TEXT_JUSTIFY_CENTER);

        if (_bitmap != null) {
            var bw = _meta.get("w");
            var bh = _meta.get("h");
            dc.drawBitmap((w - bw) / 2, (h - bh) / 2, _bitmap);
        } else {
            dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
            var label = _totalRows > 0
                ? "Receiving image " + _rowsReceived.toString() + "/" + _totalRows.toString()
                : "Receiving image...";
            dc.drawText(w / 2, h / 2, Graphics.FONT_SMALL, label, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
        }

        dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h - 16, Graphics.FONT_XTINY, "tap: new question", Graphics.TEXT_JUSTIFY_CENTER);
    }

    function onBridgeEvent(eventType, payload) {
        if (eventType.equals("image_start")) {
            _meta = payload;
            _totalRows = payload.get("h");
            _rowsReceived = 0;
            _bitmap = null;
            WatchUi.requestUpdate();
        } else if (eventType.equals("image_progress")) {
            _rowsReceived = payload.get("row") + 1;
            WatchUi.requestUpdate();
        } else if (eventType.equals("image_ready")) {
            _buildBitmap(payload);
        } else if (eventType.equals("response")) {
            WatchUi.switchToView(new ResponseView(payload, false), new ResponseDelegate(), WatchUi.SLIDE_LEFT);
        } else if (eventType.equals("error")) {
            WatchUi.switchToView(new ResponseView(payload, true), new ResponseDelegate(), WatchUi.SLIDE_LEFT);
        }
    }

    function _buildBitmap(result) {
        var meta = result.get("meta");
        var rows = result.get("rows");
        var w = meta.get("w");
        var h = meta.get("h");
        var palette = meta.get("palette");

        var bitmap = new Graphics.BufferedBitmap({ :width => w, :height => h });
        var bmpDc = bitmap.getDc();
        bmpDc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        bmpDc.clear();

        for (var y = 0; y < h; y += 1) {
            var row = rows[y];
            if (row == null) {
                continue;
            }
            for (var x = 0; x < w; x += 1) {
                var paletteIndex = row[x];
                var color = palette[paletteIndex];
                bmpDc.setColor(color, color);
                bmpDc.drawPoint(x, y);
            }
        }

        _meta = meta;
        _bitmap = bitmap;
        WatchUi.requestUpdate();
    }
}

class ImageDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onTap(evt) {
        var picker = new WatchUi.TextPicker("");
        WatchUi.pushView(picker, new PromptPickerDelegate(true), WatchUi.SLIDE_UP);
        return true;
    }
}

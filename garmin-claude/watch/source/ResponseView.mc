using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Lang;

//
// Scrollable text screen used for both successful answers and error
// messages. Swipe up/down to scroll, tap to ask a new question, physical
// back / onBack pops back to the home screen (default BehaviorDelegate
// behavior, no extra code needed).
//
class ResponseView extends WatchUi.View {

    var _fullText;
    var _isError;
    var _lines;
    var _lineHeight;
    var _scrollY;
    var _maxScrollY;
    var _headerHeight;

    function initialize(fullText, isError) {
        View.initialize();
        _fullText = fullText == null ? "" : fullText;
        _isError = isError;
        _lines = null;
        _scrollY = 0;
        _headerHeight = 34;
    }

    function onShow() {
        ClaudeBridge.setListener(method(:onBridgeEvent));
    }

    function onHide() {
        ClaudeBridge.clearListener(method(:onBridgeEvent));
    }

    function _ensureWrapped(dc) {
        if (_lines != null) {
            return;
        }
        var font = Graphics.FONT_SMALL;
        _lineHeight = Graphics.getFontHeight(font) + 4;
        var maxWidth = dc.getWidth() - 40;

        _lines = [];
        var paragraphs = _splitOn(_fullText, "\n");
        for (var p = 0; p < paragraphs.size(); p += 1) {
            var words = _splitOn(paragraphs[p], " ");
            var line = "";
            for (var i = 0; i < words.size(); i += 1) {
                var candidate = line.length() == 0 ? words[i] : line + " " + words[i];
                if (dc.getTextWidthInPixels(candidate, font) > maxWidth && line.length() > 0) {
                    _lines.add(line);
                    line = words[i];
                } else {
                    line = candidate;
                }
            }
            _lines.add(line);
        }

        var visibleHeight = dc.getHeight() - _headerHeight - 30;
        var contentHeight = _lines.size() * _lineHeight;
        _maxScrollY = contentHeight - visibleHeight;
        if (_maxScrollY < 0) {
            _maxScrollY = 0;
        }
    }

    function _splitOn(text, sep) {
        var result = [];
        var start = 0;
        var idx = text.find(sep);
        while (idx != null) {
            result.add(text.substring(start, start + idx));
            start = start + idx + sep.length();
            idx = text.substring(start, text.length()).find(sep);
        }
        result.add(text.substring(start, text.length()));
        return result;
    }

    function onUpdate(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();

        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        _ensureWrapped(dc);

        var headerColor = _isError ? Graphics.COLOR_RED : Graphics.COLOR_BLUE;
        dc.setColor(headerColor, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, 14, Graphics.FONT_TINY, _isError ? "CLAUDE - ERROR" : "CLAUDE", Graphics.TEXT_JUSTIFY_CENTER);

        dc.setClip(20, _headerHeight, w - 40, h - _headerHeight - 30);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        var y = _headerHeight - _scrollY;
        for (var i = 0; i < _lines.size(); i += 1) {
            if (y > -_lineHeight && y < h) {
                dc.drawText(20, y, Graphics.FONT_SMALL, _lines[i], Graphics.TEXT_JUSTIFY_LEFT);
            }
            y += _lineHeight;
        }
        dc.clearClip();

        // Minimal scroll indicator: a short bar on the right edge.
        if (_maxScrollY > 0) {
            var trackTop = _headerHeight;
            var trackHeight = h - _headerHeight - 30;
            var thumbHeight = trackHeight * (trackHeight.toFloat() / (trackHeight + _maxScrollY).toFloat());
            if (thumbHeight < 12) {
                thumbHeight = 12;
            }
            var thumbTop = trackTop + (trackHeight - thumbHeight) * (_scrollY.toFloat() / _maxScrollY.toFloat());
            dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.fillRoundedRectangle(w - 10, thumbTop, 4, thumbHeight, 2);
        }

        dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h - 16, Graphics.FONT_XTINY, "tap: new question", Graphics.TEXT_JUSTIFY_CENTER);
    }

    function scroll(deltaPixels) {
        _scrollY += deltaPixels;
        if (_scrollY < 0) {
            _scrollY = 0;
        }
        if (_maxScrollY != null && _scrollY > _maxScrollY) {
            _scrollY = _maxScrollY;
        }
        WatchUi.requestUpdate();
    }

    function onBridgeEvent(eventType, payload) {
        // A follow-up answer can arrive while this screen is still showing
        // (e.g. user asked a follow-up from the phone app).
        if (eventType.equals("response")) {
            WatchUi.switchToView(new ResponseView(payload, false), new ResponseDelegate(), WatchUi.SLIDE_LEFT);
        } else if (eventType.equals("error")) {
            WatchUi.switchToView(new ResponseView(payload, true), new ResponseDelegate(), WatchUi.SLIDE_LEFT);
        }
    }
}

class ResponseDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onSwipe(swipeEvent) {
        var view = WatchUi.getCurrentView()[0];
        if (!(view instanceof ResponseView)) {
            return false;
        }
        var dir = swipeEvent.getDirection();
        if (dir == WatchUi.SWIPE_UP) {
            view.scroll(80);
            return true;
        } else if (dir == WatchUi.SWIPE_DOWN) {
            view.scroll(-80);
            return true;
        }
        return false;
    }

    function onTap(evt) {
        var picker = new WatchUi.TextPicker("");
        WatchUi.pushView(picker, new PromptPickerDelegate(true), WatchUi.SLIDE_UP);
        return true;
    }
}

using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Timer;
using Toybox.System;

//
// Loading state while we wait for the phone to call Claude and send a
// reply back over AppMessage. Shows animated dots plus chunk-receive
// progress once the response actually starts streaming in, and times
// out with an error if nothing comes back (e.g. phone app isn't running).
//
class ThinkingView extends WatchUi.View {

    var _promptText;
    var _dotCount;
    var _timer;
    var _startedAt;
    var _progressLabel;

    function initialize(promptText) {
        View.initialize();
        _promptText = promptText;
        _dotCount = 0;
        _progressLabel = "";
    }

    function onShow() {
        ClaudeBridge.setListener(method(:onBridgeEvent));
        _startedAt = System.getTimer();
        _timer = new Timer.Timer();
        _timer.start(method(:onTick), 500, true);
    }

    function onHide() {
        ClaudeBridge.clearListener(method(:onBridgeEvent));
        if (_timer != null) {
            _timer.stop();
            _timer = null;
        }
    }

    function onTick() {
        _dotCount = (_dotCount + 1) % 4;

        if (System.getTimer() - _startedAt > Protocol.RESPONSE_TIMEOUT_MS) {
            _timer.stop();
            _timer = null;
            WatchUi.switchToView(
                new ResponseView("Claude didn't answer in time. Check that the AskClaude companion app is running on your phone and try again.", true),
                new ResponseDelegate(),
                WatchUi.SLIDE_LEFT
            );
            return;
        }

        WatchUi.requestUpdate();
    }

    function onUpdate(dc) {
        var w = dc.getWidth();
        var h = dc.getHeight();

        dc.setColor(Graphics.COLOR_BLACK, Graphics.COLOR_BLACK);
        dc.clear();

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.32, Graphics.FONT_MEDIUM, "CLAUDE", Graphics.TEXT_JUSTIFY_CENTER);

        var dots = "";
        for (var i = 0; i < _dotCount; i += 1) {
            dots += ".";
        }
        dc.setColor(Graphics.COLOR_BLUE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.52, Graphics.FONT_MEDIUM, "Thinking" + dots, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        if (_progressLabel.length() > 0) {
            dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
            dc.drawText(w / 2, h * 0.66, Graphics.FONT_XTINY, _progressLabel, Graphics.TEXT_JUSTIFY_CENTER);
        }
    }

    function onBridgeEvent(eventType, payload) {
        if (eventType.equals("response")) {
            WatchUi.switchToView(new ResponseView(payload, false), new ResponseDelegate(), WatchUi.SLIDE_LEFT);
        } else if (eventType.equals("error")) {
            WatchUi.switchToView(new ResponseView(payload, true), new ResponseDelegate(), WatchUi.SLIDE_LEFT);
        } else if (eventType.equals("chunk_progress")) {
            var seq = payload.get("seq");
            var total = payload.get("total");
            _progressLabel = "receiving " + (seq + 1).toString() + "/" + total.toString();
        } else if (eventType.equals("image_start") || eventType.equals("image_progress")) {
            WatchUi.switchToView(new ImageView(), new ImageDelegate(), WatchUi.SLIDE_LEFT);
        }
    }
}

class ThinkingDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onBack() {
        ClaudeBridge.cancelActive();
        WatchUi.popView(WatchUi.SLIDE_DOWN);
        return true;
    }
}

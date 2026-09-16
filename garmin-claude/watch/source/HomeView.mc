using Toybox.WatchUi;
using Toybox.Graphics;
using Toybox.Lang;

//
// Idle screen: "CLAUDE" title + a big tap target that starts a new
// question. Also listens for phone-initiated answers (see docs/architecture.md
// for the "ask from your phone" flow) so an answer typed on the OnePlus 12
// can pop straight to the response screen even if the watch never opened
// the on-device text picker.
//
class HomeView extends WatchUi.View {

    function initialize() {
        View.initialize();
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

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.30, Graphics.FONT_MEDIUM, "CLAUDE", Graphics.TEXT_JUSTIFY_CENTER);

        dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.46, Graphics.FONT_TINY, "tap to ask a question", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        dc.setColor(Graphics.COLOR_BLUE, Graphics.COLOR_TRANSPARENT);
        dc.fillCircle(w / 2, h * 0.68, 34);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(w / 2, h * 0.68, Graphics.FONT_MEDIUM, "Ask", Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);
    }

    // A response can arrive here if the question was asked from the phone
    // app instead of the watch keyboard.
    function onBridgeEvent(eventType, payload) {
        if (eventType.equals("response")) {
            WatchUi.pushView(new ResponseView(payload, false), new ResponseDelegate(), WatchUi.SLIDE_UP);
        } else if (eventType.equals("error")) {
            WatchUi.pushView(new ResponseView(payload, true), new ResponseDelegate(), WatchUi.SLIDE_UP);
        }
    }
}

class HomeDelegate extends WatchUi.BehaviorDelegate {
    function initialize() {
        BehaviorDelegate.initialize();
    }

    function onSelect() {
        _startQuestion();
        return true;
    }

    function onTap(evt) {
        _startQuestion();
        return true;
    }

    function _startQuestion() {
        var picker = new WatchUi.TextPicker("");
        WatchUi.pushView(picker, new PromptPickerDelegate(false), WatchUi.SLIDE_UP);
    }
}

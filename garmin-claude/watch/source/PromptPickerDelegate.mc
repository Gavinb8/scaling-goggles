using Toybox.WatchUi;

//
// Handles the result of Toybox.WatchUi.TextPicker, the only on-watch text
// entry mechanism Connect IQ exposes (there is no microphone/speech-to-text
// API available to third-party apps on the Venu 3 -- see docs/limitations.md).
//
class PromptPickerDelegate extends WatchUi.TextPickerDelegate {

    var _isFollowUp;

    function initialize(isFollowUp) {
        TextPickerDelegate.initialize();
        _isFollowUp = isFollowUp;
    }

    function onTextEntered(text, changed) {
        if (text == null || text.length() == 0) {
            WatchUi.popView(WatchUi.SLIDE_DOWN);
            return true;
        }

        ClaudeBridge.sendPrompt(text, _isFollowUp);
        WatchUi.switchToView(new ThinkingView(text), new ThinkingDelegate(), WatchUi.SLIDE_LEFT);
        return true;
    }

    function onCancel() {
        WatchUi.popView(WatchUi.SLIDE_DOWN);
        return true;
    }
}

//
// Single entry point for opening the on-watch keyboard.
//
// TextPicker is not guaranteed to exist on every Connect IQ device, so this
// guards with `WatchUi has :TextPicker` the way Garmin's own Keyboard sample
// does. Without the check, a device lacking on-screen text entry crashes here
// instead of telling the user to ask from the phone. The manifest targets
// venu3s as well as venu3, so this is not purely theoretical.
//
module PromptEntry {

    function start(isFollowUp) {
        if (!(WatchUi has :TextPicker)) {
            WatchUi.pushView(
                new ResponseView("This watch has no on-screen keyboard. Ask from the AskClaude app on your phone instead.", true),
                new ResponseDelegate(),
                WatchUi.SLIDE_UP
            );
            return;
        }

        WatchUi.pushView(
            new WatchUi.TextPicker(""),
            new PromptPickerDelegate(isFollowUp),
            WatchUi.SLIDE_UP
        );
    }
}

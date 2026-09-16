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

using Toybox.Application;
using Toybox.WatchUi;
using Toybox.Communications;

class AskClaudeApp extends Application.AppBase {

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state) {
        ClaudeBridge.init();
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
    }

    function onPhoneMessage(msg) {
        ClaudeBridge.onPhoneMessage(msg);
    }

    function onStop(state) {
    }

    function getInitialView() {
        return [new HomeView(), new HomeDelegate()];
    }
}

function getApp() {
    return Application.getApp();
}

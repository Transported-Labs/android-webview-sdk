console.log('Content:start!');

let port = browser.runtime.connectNative("browser");

let cueSDK = {
    postMessage: function (message) {
        console.log("Sent to app: " + message);
        port.postMessage(message);
    }
}

window.wrappedJSObject.cueSDK = cloneInto(cueSDK, window, {
  cloneFunctions: true,
});

port.onMessage.addListener(message => {
    if (window.wrappedJSObject && window.wrappedJSObject.cueSDKCallback) {
        window.wrappedJSObject.cueSDKCallback(message.text);
    }
});
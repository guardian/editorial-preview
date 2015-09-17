var viewer = require('../components/viewer');
var applicationController = require('./application.js');

var errorController = require('./error');

function init() {
    window.addEventListener('popstate', onPopState);

    document.getElementById('viewer').addEventListener('load', function(e) {

        try {
            var iframeLocation = e.target.contentWindow.location;
            e.target.contentWindow.location.href; //Requied to trigger Same origin warnings
        } catch(e) {

            if (window._previewEnv === 'preview') {
                errorController.showError('This could not be detected as a Guardian page, some features will be unavailable');
            }

            return;
        }

        errorController.hideError();
        //If we get a browser error page, then don't replaceLocationHistory
        if (iframeLocation.origin !== 'null' || iframeLocation.protocol.indexOf('http') !== -1) {
            //Needs to be replace (not push) as the iframe has added it's own history entry (shakes fist).
            replaceLocationHistory(iframeLocation);
        }
    });
}

function onPopState(e) {
    if (e.state && e.state.viewerHref) {
        viewer.updateUrl(e.state.viewerHref);
    } else {
        var initialHref = window._proxyBase + window._originalPath;
        viewer.updateUrl(initialHref);
    }
}

function replaceLocationHistory(iFrameLocation) {

    //Check if it's a proxy URL (Although shouldn't get here with Same Origin)
    if (iFrameLocation.href.indexOf(window._proxyBase) === -1) {
        console.log("This isn't a proxy url");
        return;
    }

    var resourcePath = iFrameLocation.href.replace(window._proxyBase, '');

    var viewerHref = iFrameLocation.href;
    var newAppPath = window._baseAppUrl + '/' + resourcePath;

    if (newAppPath !== window.location.pathname) { //Check it's actually a different url;
        window.history.replaceState({viewerHref: viewerHref}, '', newAppPath);
        applicationController.checkDesktopEnabled();
    }

}

module.exports = {
    init:      init,
    updateUrl: replaceLocationHistory
};

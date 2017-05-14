
var {Cc, Ci, Cu} = require("chrome");
var {Class} = require('sdk/core/heritage');
var {Unknown, Factory} = require('sdk/platform/xpcom');
var {XPCOMUtils} = Cu.import("resource://gre/modules/XPCOMUtils.jsm");

const {process} = require("sdk/remote/child");
var {protocol, theme} = require("./consts");
var data = require("sdk/self").data;

// protocol handler ////////////////////////////////////////////////////////////

const nsIProtocolHandler = Ci.nsIProtocolHandler;
const contractId = "@mozilla.org/network/protocol;1?name=" + protocol;

var Chan2Protocol = Class({
    extends: Unknown,
    interfaces: [ 'nsIProtocolHandler' ],
    scheme: protocol,
    protocolFlags: nsIProtocolHandler.URI_LOADABLE_BY_ANYONE,
    get wrappedJSObject() this,

    newURI: function(aSpec, aOriginCharset, aBaseURI)
    {
        var uri = Cc["@mozilla.org/network/simple-uri;1"].createInstance(Ci.nsIURI);
        uri.spec = aSpec;
        return uri;
    },

    newChannel: function(aURI)
    {
        var ios = Cc["@mozilla.org/network/io-service;1"].getService(Ci.nsIIOService);
        var uri = null;
        // bootstrapping is necessary to free the main code from the resource urls
        uri = ios.newURI(data.url("themes/" + theme + "/bootstrap.html"), null, null);
        var channel = ios.newChannelFromURI(uri, null).QueryInterface(Ci.nsIChannel);

        return channel;
    } 
});

var factory = Factory({
    contract: contractId,
    Component: Chan2Protocol
});

process.port.on("ui-theme-changed", function(process, message) {
    theme = message;
});

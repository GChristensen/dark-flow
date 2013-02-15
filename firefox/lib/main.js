// Dark Flow
// 
// (C) 2013 g/christensen (gchristnsn@gmail.com)

var {Cc, Ci, Cu, Cr} = require("chrome");
var {Class} = require('sdk/core/heritage');
var {Unknown, Factory} = require('sdk/platform/xpcom');
var {XPCOMUtils} = Cu.import("resource://gre/modules/XPCOMUtils.jsm");

var pageMod = require("sdk/page-mod");
var data = require("sdk/self").data;
var db = require("persist");
var comm = require("comm");

var theme = "dark";

// db initialization ///////////////////////////////////////////////////////////

db.init("dark-flow-data.sqlite", 
   function (conn) 
   {
       conn.executeSimpleSQL("CREATE TABLE board(id text primary key, last_id text);");
       conn.executeSimpleSQL("CREATE TABLE forgotten(id text primary key, queue text);");
       conn.executeSimpleSQL("CREATE TABLE watch(id text primary key, board text, oppost text);");
       conn.executeSimpleSQL("CREATE TABLE settings(id text primary key, content text);");

       db.put("settings", "data_version", {"content": "1"});
   });

// protocol handler ////////////////////////////////////////////////////////////

var protocol = "chan";

const nsIProtocolHandler = Ci.nsIProtocolHandler;
const contractId = "@mozilla.org/network/protocol;1?name=" + protocol;

db.get("settings", "content", {where: "id", eq: "theme"},
  function(value)
  {   
      db.close();

      if (value)
          theme = value;

      var Chan2Protocol = Class({
          extends: Unknown,
          interfaces: [ 'nsIProtocolHandler' ],
          scheme: protocol,
          protocolFlags: nsIProtocolHandler.URI_NOAUTH |
              nsIProtocolHandler.URI_LOADABLE_BY_ANYONE,
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
      
  });
  
// xhr /////////////////////////////////////////////////////////////////////////

function create_request()
{
    return Cc["@mozilla.org/xmlextras/xmlhttprequest;1"]
        .createInstance(Ci.nsIJSXMLHttpRequest);
}

// mini SSI-like features //////////////////////////////////////////////////////

var bootstrap_script = null;

function bootstrap_js(entry_point)
{
    if (!bootstrap_script)
    {
        let req = create_request();
        req.open("get", data.url("bootstrap.js"), false);
        req.responseType = "text";
        req.send();
        bootstrap_script = req.responseText;
    }

    return bootstrap_script.replace("$entry_point", entry_point);
}

// io.cljs message handling ////////////////////////////////////////////////////

function process_messages(worker) 
{
    var opts = {file_base: data.url("")
               };

    worker.port.emit("do-init", opts);

    worker.port.on("get-pages", function(data)
    {
        comm.get_pages(create_request, worker.port, data);
    });

    worker.port.on("post-form", function(data)
    {
        comm.post_form(create_request, worker.port, data);
    });

    worker.port.on("put-data", function(data)
    {
        db.put(data.table, data.id, data.values);
    });

    worker.port.on("get-data", function(data)
    {
       db.get(data.table, data.field, data.id,
         function (result)
         {
             worker.port.emit(data.message, result);
         });
    });

    worker.port.on("del-data", function(data)
    {
       db.del(data.table, data.id);
    });

    worker.port.on("wipe-data", function(data)
    {
       db.wipe(data.table);
    });

    worker.port.on("settings-changed", function(data)
    {
        db.get("settings", "content", {where: "id", eq: "theme"},
          function(value)
          {
              if (value)
              {
                  theme = value;
                  pages = {};
              }
              worker.tab.reload();
          });
    });
}

// page bootstrapping //////////////////////////////////////////////////////////

pageMod.PageMod({
  include: protocol + "://*",
  contentScript: bootstrap_js("main"),
  onAttach: process_messages
});

pageMod.PageMod({
  include: protocol + ":settings*",
  contentScript: bootstrap_js("settings"),
  onAttach: process_messages
});

pageMod.PageMod({
  include: protocol + ":watch*",
  contentScript: bootstrap_js("inline_watch_stream"),
  onAttach: process_messages
});

pageMod.PageMod({
  include: protocol + ":images*",
  contentScript: bootstrap_js("inline_image_stream"),
  onAttach: process_messages
});

pageMod.PageMod({
  include: protocol + ":help*",
  contentScript: bootstrap_js("display_help"),
  onAttach: process_messages
});



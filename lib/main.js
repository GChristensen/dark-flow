// Dark Flow
// 
// (C) 2013 g/christensen (gchristnsn@gmail.com)

var {Cc, Ci, Cu, Cr} = require("chrome");
var {Class} = require('sdk/core/heritage');
var {Unknown, Factory} = require('sdk/platform/xpcom');
var {XPCOMUtils} = Cu.import("resource://gre/modules/XPCOMUtils.jsm");

var pageMod = require("sdk/page-mod");
var data = require("sdk/self").data;
var file = require("sdk/io/file");
var sql = require("sqlite");


var theme = "dark";

// sqlite initialization ///////////////////////////////////////////////////////

sql.init("dark-flow-data.sqlite", 
   function (db) 
   {
       db.executeSimpleSQL("CREATE TABLE board(id text primary key, last_id text);");
       db.executeSimpleSQL("CREATE TABLE forgotten(id text primary key, queue text);");
       db.executeSimpleSQL("CREATE TABLE watch(id text primary key, board text, oppost text);");
       db.executeSimpleSQL("CREATE TABLE settings(id text primary key, content text);");
   });

// protocol handler ////////////////////////////////////////////////////////////

var protocol = "chan";

const nsIProtocolHandler = Ci.nsIProtocolHandler;
const contractId = "@mozilla.org/network/protocol;1?name=" + protocol;

sql.get("settings", "content", {where: "id", eq: "theme"},
  function(value)
  {
      sql.close();

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

// xhr & multipart form handling ///////////////////////////////////////////////

function create_request()
{
    return Cc["@mozilla.org/xmlextras/xmlhttprequest;1"]
                      .createInstance(Ci.nsIJSXMLHttpRequest);
}

function build_multipart_body(o)
{
    var boundary = '---------------------------';
    boundary += Math.floor(Math.random()*32768);
    boundary += Math.floor(Math.random()*32768);
    boundary += Math.floor(Math.random()*32768);

    var body = '';
    
    for (k in o)
    {
        let item = o[k];
        if (Array.isArray(item)) item = item[0];

        if (typeof(item) == 'string')
            body += '--' + boundary
                  + '\r\nContent-Disposition: form-data; name="' + k + '"'
                  + '\r\n\r\n' + unescape(encodeURIComponent(item)) + '\r\n';
        else
            body += '--' + boundary
                  + '\r\nContent-Disposition: form-data; name="' + k + '"; ' 
                     + 'filename="' + item.name + '"'
                  + '\r\nContent-Type: ' + item.type
                  + '\r\nContent-Transfer-Encoding: binary'
                  + '\r\n\r\n' + item.data + '\r\n';
    }

    body += '--' + boundary + '--'

    return {boundary: boundary, body: body};
}

// mini SSI-like features //////////////////////////////////////////////////////

var pages = {};
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


function load_html(url)
{
    function url_is(pattern)
    {
        return url.match(new RegExp(protocol + ":" + pattern, "i"));
    }

    var page = "frontend.html";

    if (url_is("watch.*"))
        page = "watch.html";
    else if (url_is("help.*"))
        page = "manual.html";
    else if (url_is("images.*"))
        page = "images.html";
    else if (url_is("settings.*"))
        page = "settings.html";

    if (!pages[page])
    {
        let req = create_request();
        req.open("get", data.url("themes/" + theme + "/css/bootstrap.css"), false);
        req.responseType = "text";
        req.send();
        let css = req.responseText;
        
        req.open("get", data.url(page), false);
        req.send();
        let html = req.responseText;

        html = html.replace("<head>", "<head>\n<style type=\"text/css\">" + css + "</style>\n");

        pages[page] = html;
    }

    return pages[page];
}

// io.cljs message handling ////////////////////////////////////////////////////

function addon_service(worker) 
{
    var opts = {file_base: data.url(""),
                html: load_html(worker.url)};

    worker.port.emit("do-init", opts);

    worker.port.on("get-pages", function(data)
    {
        var nocache = false;
        // if the payload is string, turn off caching
        // currently this is not used anywhere
        if (!Array.isArray(data.payload))
        {
            nocache = true;
            data.payload = [data.payload];
        }

        if (data.payload.length > 0)
        {
            var responses = new Array(data.payload.length);

            function check_responses(xhr)
            {
                var ready = true;
                var has_data = false;

                for (let i = 0; i < responses.length; i++) 
                {
                    if (!responses[i])
                        ready = false;
                    else if (responses[i].error == "success")
                        has_data = true;
                }

                if (ready)
                    worker.port.emit(data.message, 
                                     {state: has_data? "ok": "error",
                                      pages: responses});
            }

            for (let i = 0; i < data.payload.length; i++) 
            {
                let n = new Number(i);

                let req = create_request();
                req.onload = function() 
                {
                    if (this.status == 200)
                        responses[n] = {error: "success", 
                                        url: data.payload[n],
                                        text: this.responseText,
                                        cookie: this.getResponseHeader("Set-Cookie")};
                    else
                        responses[n] = {error: "http_error", 
                                        url: data.payload[n],
                                        status: this.status,
                                        cookie: this.getResponseHeader("Set-Cookie")};
                    check_responses(this);
                }
                req.onerror = function() {
                    responses[n] = {error: "error"}
                    check_responses(this);
                };
                req.onabort = function() {
                    responses[n] = {error: "error"};
                    check_responses(this);
                };

                req.open("get", data.payload[n]);
                req.responseType = "text";
                if (nocache)
                    req.setRequestHeader("Cache-Control", "no-cache");
                req.send();
            }
        }
        else
            worker.port.emit(data.message, {state: "error"});
    });

    worker.port.on("post-form", function(data)
    {
        let req = create_request();
        req.onload = function() 
        {
            worker.port.emit(data.message, {state: "ok",
                                            status: this.status,
                                            text: this.responseText,
                                            location: this.getResponseHeader("Location")});
        }
        req.onerror = function(e) {
            worker.port.emit(data.message, {state: "error"});
        };
        req.ontimeout = function(e) {
            worker.port.emit(data.message, {state: "timeout"});
        };
        req.onabort = function(e) {
            worker.port.emit(data.message, {state: "error"});
        };

        multipart = build_multipart_body(data.payload.form);

        req.open("POST", data.payload.url);
        if (data.payload.timeout)
            req.timeout = data.payload.timeout;
        if (data.payload.referer)
            req.setRequestHeader("Referer", data.payload.referer);
        req.setRequestHeader("Content-Type", "multipart/form-data; boundary=" + multipart.boundary);

        req.sendAsBinary(multipart.body);
    });

    worker.port.on("put-data", function(data)
    {
        sql.put(data.table, data.id, data.values);
    });

    worker.port.on("get-data", function(data)
    {
       sql.get(data.table, data.field, data.id,
         function (result)
         {
             worker.port.emit(data.message, result);
         });
    });

    worker.port.on("del-data", function(data)
    {
       sql.del(data.table, data.id);
    });

    worker.port.on("wipe-data", function(data)
    {
       sql.wipe(data.table);
    });

    worker.port.on("settings-changed", function(data)
    {
        sql.get("settings", "content", {where: "id", eq: "theme"},
          function(value)
          {
              if (value)
              {
                  theme = value;
                  pages = {};
              }
          });
    });
}

// page bootstrapping //////////////////////////////////////////////////////////

pageMod.PageMod({
  include: protocol + "://*",
  contentScript: bootstrap_js("kuroi.frontend.main()"),
  onAttach: addon_service
});

pageMod.PageMod({
  include: protocol + ":settings*",
  contentScript: bootstrap_js("kuroi.settings.main()"),
  onAttach: addon_service
});

pageMod.PageMod({
  include: protocol + ":watch*",
  contentScript: bootstrap_js("kuroi.frontend.inline_watch_stream()"),
  onAttach: addon_service
});

pageMod.PageMod({
  include: protocol + ":images*",
  contentScript: bootstrap_js("kuroi.frontend.inline_image_stream()"),
  onAttach: addon_service
});

pageMod.PageMod({
  include: protocol + ":help*",
  contentScript: bootstrap_js("kuroi.frontend.display_help()"),
  onAttach: addon_service
});



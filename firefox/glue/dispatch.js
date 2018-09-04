// Dark Flow
// 
// (C) 2013 g/christensen (gchristnsn@gmail.com)

const data_version = "1";

let gport = new IOPort(dispatch_messages);

let dispatch = {};

dispatch.init = function(bootstrap_settings)
{
    let search = window.location.search;
    let entry_point = "urlbar";
    let resource = "";

    if (search && search.length > 1) {
        let vars = window.location.search.substring(1).split("&");
        entry_point = vars[0];

        for (let v of vars)
            if (v.startsWith("url="))
                resource = decodeURI(v.split("=")[1]);
    }

    let opts = {
        port: gport,
        file_base: "",
        addon: true,
        entry_point: entry_point,
        resource: entry_point === "urlbar"? bootstrap_settings.last_url: resource
    };

    // sic!
    frontend = kuroi.frontend;

    persist.init("dark-flow-data.indexed", data_version,
        function _db_upgrade(e)
        {
            var db = e.target.result;
            var store;
            store = db.createObjectStore("board", { keyPath: "id" });
            store.createIndex("id", "id", { unique: true });
            store = db.createObjectStore("forgotten", { keyPath: "id" });
            store.createIndex("id", "id", { unique: true });
            store = db.createObjectStore("watch", { keyPath: "id" });
            store.createIndex("id", "id", { unique: true });
            store.createIndex("board", "board", { unique: false });
            store = db.createObjectStore("settings", { keyPath: "id" });
            store.createIndex("id", "id", { unique: true });
        },
        function _db_success(e)
        {
            if (goog.require)
            {
                goog.require("kuroi.io");
                goog.require("kuroi.settings");
                goog.require("kuroi.frontend");
            }

            kuroi.io.init(opts);
            kuroi.frontend.init(opts);

            kuroi.settings.load_settings(function(settings)
            {
                let module_main = eval("kuroi.frontend." + opts.entry_point);
                module_main(settings, opts.resource);
            });
        });
};

// xhr /////////////////////////////////////////////////////////////////////////

function create_request()
{
    return new XMLHttpRequest(); 
}

// kuroi.io.cljs message handling //////////////////////////////////////////////

function dispatch_messages(msg, data, callback)
{
    switch (msg) {
    case "get-pages":
        net.get_pages(create_request, callback, "", data);
        break;

    case "post-form":
        net.post_form(create_request, callback, "", data);
        break;

    case "put-data":
        persist.put(data.table, data.id, data.values);
        break;

    case "get-data":
       persist.get(data.table, data.field, data.id,
         function (result)
         {
             callback(result);
         },
         false);
        break;

    case "del-data":
       persist.del(data.table, data.id, false);
        break;

    case "wipe-data":
       persist.wipe(data.table);
       break;

    case "settings-changed":
        persist.get("settings", "content", {where: "id", eq: "theme"},
          function(value)
          {
              if (value)
              {
                  theme = value[0];
                  console.log("theme stored")
                  console.log(theme)

                  browser.storage.local.get(null, )
                  browser.storage.local.set({theme: theme});
              }
          });
        break;
    case "url-followed":
        browser.storage.local.set({last_url: data});
        break;
    case "load-threads":
        if (data.parent) {
            data.message = msg;
            data.parent = false;
            window.parent.postMessage(data, "*");
        }
        else
            callback(data);
        break;
    case "follow-url":
        if (data.parent) {
            data.message = msg;
            data.parent = false;
            window.parent.postMessage(data, "*");
        }
        else
            location.href = data.url;
        break;
    case "dark-flow:post-form-iframe-submitted":
    case "dark-flow:post-form-iframe-loaded":
        callback();
        break;
    }
}

// background script message handling /////////////////////////////////////////

window.addEventListener("message", (event) => {
    gport.emit(event.data.message, event.data);
});

browser.runtime.onMessage.addListener (msg => {
    gport.emit(msg.message, msg);
});
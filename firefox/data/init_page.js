function init_page(opts)
{
    if (goog.require)
    {
        goog.require("kuroi.io");
        goog.require("kuroi.settings");
        goog.require("kuroi.frontend");
    }

    frontend = kuroi.frontend;

    kuroi.io.init(opts);
    kuroi.frontend.init(opts);

    kuroi.settings.load_settings(function(settings)
    {
        let module_main = eval("kuroi.frontend." + opts.entry_point);
        module_main(settings, opts.resource);
    });
};

var global_event_callbacks = {};

function global_dispatch_msg(data)
{
    if (data.msg == "dark-flow:initialize")   
    {
      port = {once: function (msgid, callback)
              {                
                  global_event_callbacks[msgid] = callback;
                  window.postMessage({msg: "dark-flow:portIOonce", msgid: msgid}, "*");
              }, 
              emit: function (a1, a2)
              {
                  //console.error(a1 + ": " + a2.message);
                  window.postMessage({msg: "dark-flow:portIOemit", arg1: a1, arg2: a2}, "*");
              }};

      entry_point = data.opts.main;
      file_base = data.opts.file_base;
      protocol = data.opts.protocol;
      scheme = data.opts.scheme;

      // inject frontend.js into the document
      let script = document.createElement("script");

      script.type = "text/javascript";
      script.src = data.opts.file_base + "frontend.js";  
      script.setAttribute("onload", "init_page({port: port, file_base: file_base, resource: document.location.href, "
                                  + "protocol: protocol, scheme: scheme, addon: true, entry_point: entry_point})");
      document.head.appendChild(script);

    }
    else if (data.msg == "dark-flow:portIOonce-response")
    {
       let callback = global_event_callbacks[data.msgid];

       if (callback)
       {
         delete global_event_callbacks[data.msgid];
         callback(data.response);
       }
    }
}

window.postMessage({msg: "dark-flow:init-script-loaded"}, "*");
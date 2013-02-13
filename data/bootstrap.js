// content script for the bootstrap.html page

self.port.on("do-init", function(opts) 
{
    var uw = unsafeWindow;
    var doc = uw.document;

    opts.port = self.port;

    // replace content of the page by the supplied html
    doc.documentElement.innerHTML = opts.html;

    uw.on_frontend_load = function ()
    {
        if (uw.goog.require)
        {
            uw.goog.require("kuroi.io");
            uw.goog.require("kuroi.settings");
            uw.goog.require("kuroi.frontend");
        }

        uw.frontend = uw.kuroi.frontend;

        // initialization of the io.cljs module
        uw.kuroi.io.init(opts);
        // entry point for the page is inserted by the addon
        uw.$entry_point;
    };

    // inject frontend.js to the document
    script = doc.createElement("script");

    script.type = "text/javascript";
    script.src = opts.file_base + "frontend.js";
    script.setAttribute("onload", "on_frontend_load()");

    doc.head.appendChild(script);
});
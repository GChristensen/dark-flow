// content script for the bootstrap.html page

self.port.on("do-init", function(opts) 
{
    var uw = unsafeWindow;
    var doc = uw.document;

    opts.port = self.port;

    uw.init_page = function ()
    {
        if (uw.goog.require)
        {
            uw.goog.require("kuroi.io");
            uw.goog.require("kuroi.settings");
            uw.goog.require("kuroi.frontend");
        }

        uw.frontend = uw.kuroi.frontend;

        // initialization of the cljs modules
        uw.kuroi.io.init(opts);
        uw.kuroi.frontend.init(opts.file_base);

        // an entry point for the page is inserted by the addon        
        uw.kuroi.settings.load_settings(function(settings)
          {
              uw.kuroi.frontend.$entry_point(settings);
          });
    };

    // inject frontend.js into the document
    script = doc.createElement("script");

    script.type = "text/javascript";
    script.src = opts.file_base + "frontend.js";
    script.setAttribute("onload", "init_page()");

    doc.head.appendChild(script);
});

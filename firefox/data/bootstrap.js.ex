// content script for the bootstrap.html page

self.port.on("do-init", function(opts) 
{
    var uw = unsafeWindow;
    var doc = uw.document;

    function portIOonce(arg1, arg2)
    {
        self.port.once(arg1, arg2);
    }

    function portIOemit(arg1, arg2)
    {
        self.port.emit(arg1, arg2);
    }

    exportFunction(portIOonce, uw, {defineAs: "portIOonce"});
    exportFunction(portIOemit, uw, {defineAs: "portIOemit"});

    // global objects available under the page context, used in the frontend.js onload handler
    //uw.port = {once: uw.portIOonce, emit: uw.portIOemit};
    uw.file_base = opts.file_base;
    uw.protocol = opts.protocol;
    uw.scheme = opts.scheme;

    // inject init_page.js into the document
    script = doc.createElement("script");

    script.type = "text/javascript";
    script.src = opts.file_base + "init_page.js";
    doc.head.appendChild(script);

    // inject frontend.js into the document
    script = doc.createElement("script");

    script.type = "text/javascript";
    script.src = opts.file_base + "frontend.js";        // an entry point for the page is inserted by the addon        
    script.setAttribute("onload", "init_page(kuroi.frontend.$entry_point, "
                                + "{port: port, file_base: file_base, resource: document.location.href, "
                                + "protocol: protocol, scheme: scheme, addon: true})");
    doc.head.appendChild(script);
});

function init_page(entry_point, opts)
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
        entry_point(settings, opts.resource);
    });
};
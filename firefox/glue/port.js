var IOPort = function(dispatcher) {

    this.callbacks = {};

    this.once = function (msgid, callback)
    {
        this.callbacks[msgid] = callback;
    };

    this.emit = function (a1, a2)
    {
        let callback = this.callbacks[a2.message];

        if (callback)
        {
            delete this.callbacks[a2.message];
        }
        dispatcher(a1, a2, callback);
    }
};
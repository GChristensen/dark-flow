var IOPort = function(dispatcher) {

    this.callbacks = {};

    this.permanentCallbacks = {};

    this.on = function (msgid, callback)
    {
        this.permanentCallbacks[msgid] = callback;
    };

    this.once = function (msgid, callback)
    {
        this.callbacks[msgid] = callback;
    };

    this.emit = function (a1, a2)
    {
        let callback = this.permanentCallbacks[a1];

        if (!callback && a2.message)
            callback = this.callbacks[a2.message];

        if (callback && this.callbacks[a2.message])
        {
            delete this.callbacks[a2.message];
        }

        dispatcher(a1, a2, callback);
    }
};
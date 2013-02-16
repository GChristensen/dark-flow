// Dark Flow
// 
// (C) 2013 g/christensen (gchristnsn@gmail.com)

// An implementation of the Firefox addon sdk port interface.
// This allows to seamlessly plug into the frontend.js.
port = {
    events: {},
    on: function(event, handler)
        {
            this.events[event] = {handler: handler, once: false};
        },
    once: function(event, handler)
        {
            this.events[event] = {handler: handler, once: true};
        },
    emit: function(event, data)
        {
            var item = this.events[event];
            if (item)
            {
                if (item.once)
                    delete this.events[event];
                item.handler(data);
            }
        }
}

function create_request()
{
    return new XMLHttpRequest();
}

persist.init("dark-flow-data.indexed", 1,
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
             });
             
port.on("get-pages", function(data)
        {
            get_pages(create_request, port, "/get/", data);
        });

port.on("post-form", function(data)
        {
            post_form(create_request, port, "/post/", data);
        });

port.on("put-data", function(data)
        {
            persist.put(data.table, data.id, data.values);
        });

port.on("get-data", function(data)
        {
            persist.get(data.table, data.field, data.id,
                   function (result)
                   {
                       port.emit(data.message, result);
                   });
        });

port.on("del-data", function(data)
        {
            persist.del(data.table, data.id);
        });

port.on("wipe-data", function(data)
        {
            persist.wipe(data.table);
        });

port.on("settings-changed", function(data)
    {
        window.parent.location.reload();
    });

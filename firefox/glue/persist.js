// Dark Flow
// 
// (C) 2013 g/christensen (gchristnsn@gmail.com)

var persist = {};

var _conn = null;

function open_db()
{
    return _conn;
}

persist.init = function init(database, ver, init, success)
{
    var request = indexedDB.open(database, ver);
    request.onupgradeneeded = init;

    request.onsuccess = function (e)
    {
        _conn = e.target.result;
        success(e);
    };

    request.onerror = function(e) {console.log(e)};
};


persist.put = function (table, id, values)
{
    var db = open_db();
    if (db)
    {
        var transaction = db.transaction([table], "readwrite");

        transaction.oncomplete = function(event)
        {
        };

        transaction.onerror = function(event)
        {
            console.log(event);
        };

        values["id"] = id;
        var objectStore = transaction.objectStore(table);
        var request = objectStore.put(values);
        request.onsuccess = function(event)
        {
        };
    }
};


persist.get = function (table, field, id, success)
{
    var db = open_db();
    if (db)
    {
        var transaction = db.transaction([table]);

        transaction.oncomplete = function(event)
        {
            //console.log(event);
        };

        transaction.onerror = function(event)
        {
            success(null);
        };

        var objectStore = transaction.objectStore(table);

        var id_field = id? id.where: null;

        var index = id? objectStore.index(id_field): objectStore;

        var result = [];
        var range = id_field? IDBKeyRange.only(id.eq): null;

        index.openCursor(range).onsuccess = function(e)
        {
            var cursor = e.target.result;

            if (cursor)
            {
                result.push(cursor.value[field]);
                cursor.continue();
            }
            else
            {
                success(result);
            }
        }
    }
    else
        success(null);
}

persist.del = function (table, id)
{
    var db = open_db();
    if (db)
    {
        var transaction = db.transaction([table], "readwrite");

        transaction.oncomplete = function(event)
        {
        };

        transaction.onerror = function(event)
        {
        };

        var objectStore = transaction.objectStore(table);
        objectStore.delete(id);
    }
}

persist.wipe = function (table)
{
    var db = open_db();
    if (db)
    {
        var transaction = db.transaction([table], "readwrite");

        transaction.oncomplete = function(event)
        {
        };

        transaction.onerror = function(event)
        {
        };

        var objectStore = transaction.objectStore(table);
        objectStore.clear();
    }
}
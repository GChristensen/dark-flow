// Dark Flow
// 
// (C) 2013 g/christensen (gchristnsn@gmail.com)

persist = {};

window.indexedDB = window.indexedDB || window.webkitIndexedDB;
window.IDBDatabase = window.IDBDatabase || window.webkitIDBDatabase;
window.IDBTransaction = window.IDBTransaction || window.webkitIDBTransaction;
window.IDBKeyRange = window.IDBKeyRange || window.webkitIDBKeyRange;
window.IDBIndex = window.IDBIndex || window.webkitIDBIndex;
window.IDBCursor = window.IDBCursor || window.webkitIDBCursor;
window.IDBObjectStore = window.IDBObjectStore || window.webkitIDBObjectStore;
window.IDBDatabaseError = window.IDBDatabaseError || window.webkitIDBDatabaseError;
window.IDBDatabaseException = window.IDBDatabaseException || window.webkitIDBDatabaseException;
window.IDBFactory = window.IDBFactory || window.webkitIDBFactory;

var chrome_mobile = !!window.webkitIDBDatabase;

var _conn = null;

function open_db()
{
    return _conn;
}

persist.init = function init(database, ver, init, success) 
{        
    var request = chrome_mobile
        ? indexedDB.open(database)
        : indexedDB.open(database, ver);

    if (!chrome_mobile)
        request.onupgradeneeded = init;

    request.onsuccess = function (e) 
    {
        _conn = e.target.result;
        if (chrome_mobile)
        {
        	if (_conn.version < ver)
        	{
        		_conn.setVersion(ver).onsuccess = function()
                {   
        		    init(e);
                }
        	}
        }   
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

exports.wipe = function (table) 
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


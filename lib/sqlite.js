// Dark Flow
// 
// (C) 2013 g/christensen (gchristnsn@gmail.com)

const {Cc, Ci} = require("chrome");

const fileDirectoryService = Cc["@mozilla.org/file/directory_service;1"].
                            getService(Ci.nsIProperties).
                            get("ProfD",Ci.nsIFile);

const storageService = Cc["@mozilla.org/storage/service;1"].
                        getService(Ci.mozIStorageService);

var db_file;
var conn;

function open_db()
{
    if (conn)
        return conn;
    else
    {
        conn = storageService.openDatabase(db_file);
        return conn;
    }
}

exports.init = function init(database, init) 
{
    fileDirectoryService.append(database);
    db_file = fileDirectoryService;

    var exists = db_file.exists();
    var connection = storageService.openDatabase(db_file);

    try
    {
      if (!exists && init)
          init(connection);
    }
    finally
    {
        connection.asyncClose();
    }
}

function q_esc(s)
{
    if (s) return s.replace(/(['])/g, "''");
}

function kv_set(values)
{
    var r = ""; 
    for (var k in values)
    {
        r += k + " = '" + q_esc(values[k]) + "',";
    }   
    
    return r.substring(0, r.length - 1);
}

function v_join(values, id)
{
    var r = "";
    for (var k in values)
    {
        r += "'" + q_esc(values[k]) + "',";
    }   
    
    return r + "'" + q_esc(id) + "'";
}

exports.put = function (table, id, values) 
{
    var connection = open_db();
    let exists_st = connection.createStatement
    (
        "select exists(select 1 from " + table + " where id = :id) as e"
    );
    exists_st.bindStringParameter(0, id);
    exists_st.executeStep();

    let set_st = connection.createStatement
    (
        exists_st.row.e > 0
            ? ("update " + table + " set " + kv_set(values) + " where id = :id")
            : (  "insert into " + table + "(" + Object.keys(values).join() 
                 + ",id) values(" + v_join(values, id) + ")")
    );

    if (exists_st.row.e > 0)
        set_st.bindStringParameter(0, id);

    set_st.execute();   
}

exports.get = function (table, field, id, success) 
{
    var connection = open_db();
    let statement = connection.createStatement
    (
        "select " + field + " from " + table + 
            (!id ? ""
                 : (" where " + id.where + " = :id"))
    );

    if (id)
        statement.bindStringParameter(0, id.eq);

    statement.executeAsync({
        handleResult: function(aResultSet) {
            var result = [];
            for (let row = aResultSet.getNextRow();
                 row;
                 row = aResultSet.getNextRow()) {
                
                result.push(row.getResultByName(field));
            }
            this._result_obtained = true;
            success(result);
        },
        
        handleError: function(aError) {
            console.log("Error: " + aError.message);
        },
        
        handleCompletion: function(aReason) {
            if (!this._result_obtained)
                success(null);
        }
    });
}

exports.del = function (table, id) 
{
    var connection = open_db();
    let statement = connection.createStatement
    (
        "delete from " + table + " where id = :id"
    );

    statement.bindStringParameter(0, id);
    statement.execute();
}

exports.wipe = function (table) 
{
    var connection = open_db();
    connection.executeSimpleSQL("delete from " + table);
}

exports.close = function (table) 
{
    if (conn)
    {
        conn.asyncClose();
        conn = null;
    }
}


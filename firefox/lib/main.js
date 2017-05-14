// Dark Flow
// 
// (C) 2013 g/christensen (gchristnsn@gmail.com)

const {processes, remoteRequire} = require("sdk/remote/parent");
const {XMLHttpRequest} = require("sdk/net/xhr");

var privateBrowsing = require("sdk/private-browsing");
var {protocol, theme, db_file} = require("./consts");
var events = require("sdk/system/events");
var pageMod = require("sdk/page-mod");
var data = require("sdk/self").data;
var db = require("./persist");
var comm = require("./comm");

// db initialization ///////////////////////////////////////////////////////////

var data_version = "3";

db.init(db_file, 
   function (conn) 
   {
       conn.executeSimpleSQL("CREATE TABLE board(id text primary key, last_id text);");
       conn.executeSimpleSQL("CREATE TABLE forgotten(id text primary key, queue text);");
       conn.executeSimpleSQL("CREATE TABLE watch(id text primary key, board text, oppost text);");
       conn.executeSimpleSQL("CREATE TABLE settings(id text primary key, content text);");

       db.put("settings", "data_version", {"content": "1"});
   });

db.get("settings", "content", {where: "id", eq: "data_version"},
  function(value)
  {
      var i_ver = parseInt(value);
      if (i_ver < parseInt(data_version))
      {
          var conn = db.open();
          if (i_ver < 2)
              conn.executeSimpleSQL("ALTER TABLE board ADD COLUMN seen text;");
          if (i_ver < 3)
              conn.executeSimpleSQL("ALTER TABLE board ADD COLUMN expanded text;");

          db.put("settings", "data_version", {"content": data_version});
      }
  });

db.get("settings", "content", {where: "id", eq: "theme"},
  function(value)
  {   
      db.close();

      if (value)
          theme = value;

  });

events.on("last-pb-context-exited", function (event) {db.purge_in_memory_db();});

// protocol handler ////////////////////////////////////////////////////////////

remoteRequire("./protocol", module);
  
// xhr /////////////////////////////////////////////////////////////////////////

function create_request()
{
    return new XMLHttpRequest(); 
}

// mini SSI-like features //////////////////////////////////////////////////////

var bootstrap_script = null;

function bootstrap_js(entry_point)
{
    if (!bootstrap_script)
    {
        let req = create_request();
        req.open("get", data.url("bootstrap.js"), false);
        req.send();
        bootstrap_script = req.responseText;
    }
    return bootstrap_script.replace("$entry_point", entry_point);
}
 
// io.cljs message handling ////////////////////////////////////////////////////

function process_messages(worker) 
{
    var opts = {file_base: data.url(""),
                protocol: protocol,
                scheme: protocol + "://"
               };

    worker.port.emit("do-init", opts);

    worker.port.on("get-pages", function(data)
    {
        comm.get_pages(create_request, worker.port, "", data);
    });

    worker.port.on("post-form", function(data)
    {
        comm.post_form(create_request, worker.port, "", data);
    });

    worker.port.on("put-data", function(data)
    {
        db.put(data.table, data.id, data.values, privateBrowsing.isPrivate(worker));
    });

    worker.port.on("get-data", function(data)
    {
       db.get(data.table, data.field, data.id,
         function (result)
         {
             worker.port.emit(data.message, result);
         },
         privateBrowsing.isPrivate(worker));
    });

    worker.port.on("del-data", function(data)
    {
       db.del(data.table, data.id, privateBrowsing.isPrivate(worker));
    });

    worker.port.on("wipe-data", function(data)
    {
       db.wipe(data.table);
    });

    worker.port.on("settings-changed", function(data)
    {
        db.get("settings", "content", {where: "id", eq: "theme"},
          function(value)
          {
              if (value)
              {
                  theme = value;
                  processes.port.emit("ui-theme-changed", theme);
              }
              worker.tab.reload();
          });
    });
}

// page bootstrapping //////////////////////////////////////////////////////////

function cons_page_mod(entry_point)
{
    return pageMod.PageMod({
        include: protocol + ":" + (entry_point? (entry_point + "*"): "//*"),
        contentScript: bootstrap_js(entry_point? entry_point: "main"),
        onAttach: process_messages
    });
}

cons_page_mod();
cons_page_mod("help");
cons_page_mod("watch");
cons_page_mod("video");
cons_page_mod("images");
cons_page_mod("settings");

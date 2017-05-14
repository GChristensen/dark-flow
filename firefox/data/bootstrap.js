// content script for the bootstrap.html page

self.port.on("do-init", function(opts) 
{
    window.addEventListener('message', function(event) {
      if (event.data.msg == "dark-flow:init-script-loaded")   
      {
         opts.main = "$entry_point"; // will be replaced by addon
         window.postMessage({msg: "dark-flow:initialize", opts: opts}, "*"); 
      }
      if (event.data.msg == "dark-flow:portIOonce")
      {
        self.port.once(event.data.msgid, function (response) 
        {
            window.postMessage({msg: "dark-flow:portIOonce-response", msgid: event.data.msgid, response: response}, "*");        
        });
      }
      else if (event.data.msg == "dark-flow:portIOemit")
      {
        self.port.emit(event.data.arg1, event.data.arg2);
      }

    }, false);

    window.postMessage({msg: "dark-flow:load-init-script", file_base: opts.file_base}, "*");    
});

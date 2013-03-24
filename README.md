Dark Flow 
=========

An advanced imageboard aggregator.

Dark Flow is a complete client-side port of the 
[Dark Matter](https://github.com/GChristensen/dm-browser#readme) 
web-application, which includes all feautres of the former except the archive 
functionality.

Currently Dark Flow could run as:
* A Firefox addon ([on github](https://github.com/GChristensen/dark-flow/blob/master/firefox/dark-flow-aggregator.xpi?raw=true))
* A web-application in a servlet container (including GAE). The web-app uses IndexedDB to 
  store data on a client, this narrows its compatibility to Firefox and Chrome browsers
  (including Android versions).

See the [manual](https://raw.github.com/GChristensen/dark-flow/master/manual.png) for more information.
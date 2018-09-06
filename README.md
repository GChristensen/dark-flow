Dark Flow <sup>EXPERIMENTAL</sup>
=========

A command interface for imageboards.

[DOWNLOAD (Firefox Add-On)](https://github.com/GChristensen/dark-flow/releases/download/v0.2.0.24a/dark_flow.xpi) :: [VIDEO MANUAL](https://www.youtube.com/watch?v=QWI2CNt-snQ)

![Dark Flow video](screen.png?raw=true)

SEE ALSO: [UbiquityWE](https://github.com/GChristensen/ubichr#readme)

#### Description

Allows to unify and enrich user experience of Wakaba-style imageboards through 
additional functionality, data transformations and simple command language. Generally,
it can read any Wakaba-flavour imageboard which is not too different from the original
Wakaba. Parser-plugins for particularly any forum could be written in Clojure script. 
See the [manual](https://raw.github.com/GChristensen/dark-flow/master/manual.png) 
or [video](https://www.youtube.com/watch?v=QWI2CNt-snQ) for more information.


#### Background

At first the project was implemented just for fun as the [dm-browser](https://github.com/GChristensen/dm-browser) 
GAE-application in an early version of Clojure (at the times when Clojure script was even not 
versioned) and then ported to Clojure Script to embed it in Firefox 
to enter chan:// style urls in address bar directly (this was necessary
to distinguish them from regular http:// urls and apply special processing). 
It was reworked again after Clojure Script introduced a set of breaking changes
and when multiprocess Firefox has appeared.
When Firefox quantum has broken it, it was ported back to GAE, and finally again 
to Firefox Quantum. As the result, the current codebase consists exclusively of hacks and obscure
control streams needed to overcome all the limitations encountered in the past.
 
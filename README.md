Dark Flow <sup>EXPERIMENTAL</sup>
=========

A command interface for paginated forums.

This is a development site. Please visit the main site at: https://gchristensen.github.io/dark-flow/


#### Background

At first the project was implemented just for fun as the [dm-browser](https://github.com/GChristensen/dm-browser#readme) 
GAE-application in an early version of Clojure (at the times when Clojure script was even not 
versioned) and then ported to Clojure Script to embed it in Firefox 
to enter chan:// style urls in address bar directly (this was necessary
to distinguish them from regular http:// urls and apply special processing). 
It was reworked again after Clojure Script introduced a set of breaking changes
and when multiprocess Firefox has appeared.
When Firefox quantum has broken it once more, it was ported back to GAE, and finally again 
to Firefox Quantum. As the result, the current codebase consists exclusively of hacks and obscure
control streams needed to overcome all the limitations encountered in the way.
 
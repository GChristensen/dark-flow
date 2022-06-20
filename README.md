Dark Flow <sup>EXPERIMENTAL</sup>
=========

A command interface for paginated forums.

This is a development site. Please visit the main site at: https://gchristensen.github.io/dark-flow/


#### Background

At first the project was implemented just for fun as the
[dm-browser](https://github.com/GChristensen/dm-browser#readme) GAE-application
in an early version of Clojure (at the times when Clojure script was even not
versioned) and then ported to Clojure Script to embed it in Firefox to enter
chan:// style urls in address bar directly (this was necessary to distinguish
them from regular http:// urls and apply special processing). It was reworked
again after Clojure Script introduced a set of breaking changes, and yet again
when multiprocess Firefox has appeared. When Firefox quantum has broken it once
more, it was ported back to GAE, and finally again as a Firefox Quantum
extension. WebExtension manifest v3 added a yet another layer of indirection. 
As the result, the current codebase consists exclusively of hacks and
obscure control streams needed to overcome all the limitations encountered in
the way. Because application uses aged libraries and dynamic techniques inside
dynamic techniques to generate content, because it was written before the era
of JS SPA frameworks, it needs to be scrapped and rewritten from scratch.
In short, brace yourself if you want to look at the sources.
 
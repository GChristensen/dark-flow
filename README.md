Dark Flow <sup>EXPERIMENTAL</sup>
=========

A command interface for paginated forums.

![Firefox](https://github.com/GChristensen/gchristensen.github.io/blob/master/firefox.png?raw=true)
[Firefox Add-On](https://github.com/GChristensen/dark-flow/releases/download/v0.2.1.4/dark_flow.xpi)
:: ![Chrome](https://github.com/GChristensen/gchristensen.github.io/blob/master/chrome.png?raw=true)
[Chrome Extension](https://chrome.google.com/webstore/detail/dark-flow/ioflckpnjkfjfbnpbeobnbfcmipanhce)
:: ![Youtube](https://github.com/GChristensen/gchristensen.github.io/blob/master/youtube.png?raw=true)
 [Video Manual](https://youtu.be/tNPRCNruWmI)
 
![Dark Flow video](screen.png?raw=true)

SEE ALSO: [UbiquityWE](https://github.com/GChristensen/ubiquitywe#readme)

#### Description

Allows to unify and enrich user experience of Wakaba-style imageboards through 
additional functionality, data transformations and simple command language. Generally,
it can read any Wakaba-flavour imageboard which is not too different from the original
Wakaba. Parser-plugins for particularly any forum could be written in Clojure script. 
See the [manual](https://raw.github.com/GChristensen/dark-flow/master/manual.png) 
or [video](https://www.youtube.com/watch?v=QWI2CNt-snQ) for more information.

#### UbiquityWE integration

Use the following command to follow dark-flow URLs from UbiquityWE:

```javascript
CmdUtils.CreateCommand({
    name: "dark-flow",
    uuid: "https://github.com/GChristensen/dark-flow",
    arguments: [{role: "object", nountype: noun_arb_text, label: "URL"}],
    description: "Follow URLs in <a href='https://github.com/GChristensen/dark-flow#readme'>Dark Flow</a>.",
    icon: "/commands/more/dark-flow.png",
    execute: function execute({object: {text}}) {
        chrome.runtime.sendMessage("dark-flow@firefox", {message: "dark-flow:follow-url", url: text}, null);
    },
    preview: "Follow the URL in Dark Flow"
});
```

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
 
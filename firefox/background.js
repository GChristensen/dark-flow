function openBoard() {
   browser.tabs.create({
     "url": "flow.html"
   });
}

browser.browserAction.onClicked.addListener(openBoard);

browser.runtime.onMessage.addListener(msg => {
   switch (msg.message) {
       case "dark-flow:follow-url":
           browser.tabs.create({
               "url": "flow.html" + "?front&url=" + encodeURI(msg.url)
           });
           break;
   }
});
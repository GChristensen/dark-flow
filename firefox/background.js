function openBoard() {
   browser.tabs.create({
     "url": "flow.html"
   });
}

browser.browserAction.onClicked.addListener(openBoard);

browser.runtime.onMessage.addListener(msg => {
    console.log("dark-flow runtime message");
    console.log(msg);
   switch (msg.message) {
       case "dark-flow:follow-url":
           //window.location.href = "?front&url=" + encodeURI(msg.url);
           break;
   }
});
function openBoard() {
   browser.tabs.create({
     "url": "flow.html"
   });
}

browser.browserAction.onClicked.addListener(openBoard);
function openBoard() {
   browser.tabs.create({
     "url": "board.html"
   });
}

browser.browserAction.onClicked.addListener(openBoard);
async function openBoard() {

    await browser.permissions.request({origins: ["<all_urls>"]});

    chrome.tabs.create({
     "url": "flow.html"
   });
}

(chrome.browserAction || chrome.action).onClicked.addListener(openBoard);

chrome.runtime.onMessageExternal.addListener(msg => {
   switch (msg.message) {
       case "dark-flow:follow-url":
           chrome.tabs.create({
               "url": "flow.html" + "?front&url=" + encodeURI(msg.url)
           });
           break;
   }
});
const MANIFEST_V3 = chrome.runtime.getManifest().manifest_version === 3;

async function openBoard() {
    if (MANIFEST_V3)
        await browser.permissions.request({origins: ["<all_urls>"]});

    chrome.tabs.create({url: "flow.html"});
}

(MANIFEST_V3? chrome.action: chrome.browserAction).onClicked.addListener(openBoard);

chrome.runtime.onMessageExternal.addListener(msg => {
   switch (msg.message) {
       case "dark-flow:follow-url":
           chrome.tabs.create({
               "url": "flow.html" + "?front&url=" + encodeURI(msg.url)
           });
           break;
   }
});
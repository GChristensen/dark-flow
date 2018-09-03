window.addEventListener("load", () => {

    browser.storage.local.get(null, (bootstrap_settings) => {

        let nodes = document.querySelectorAll("html, body");
        nodes.forEach((n => {
            n.style.backgroundColor = bootstrap_settings.theme === "light"
                ? "#F0E0D6"
                : "#181818";
        }));

        dispatch.init(bootstrap_settings)
    });

});
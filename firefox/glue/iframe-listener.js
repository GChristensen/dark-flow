
console.log(location)

if (window.parent != window && location.pathname.endsWith("/post")) {// 4chan in iframe
    chrome.runtime.sendMessage({message: "dark-flow:post-form-iframe-submitted"})
}

if (location.hash && location.hash.startsWith("#form")) {

    let form = document.body.querySelector("form[name='post']"); // 4chan in iframe

    if (form) {
        document.body.innerHTML = "";

        let postForm = form.querySelector(".postForm");
        if (postForm)
            postForm.setAttribute("style", "display: block !important");
        let captcha_line = form.querySelector("#captchaFormPart");

        if (captcha_line) {
            let captcha_script = captcha_line.querySelector("script");
            captcha_script.setAttribute("async", "false");

            let table = captcha_line.parentNode;
            table.removeChild(captcha_line);
            table.insertBefore(captcha_line, table.firstChild);
        }
        document.body.appendChild(form);

        setTimeout(() => {
            let newScript = document.createElement('script');
            newScript.setAttribute("async", "false");
            newScript.innerHTML = 'initRecaptcha()';
            document.head.appendChild(newScript);
        }, 1000);

        form.addEventListener("submit", (e) => {
            e.preventDefault();

            console.log("submitted")
            let newScript = document.createElement('script');
            newScript.innerHTML = `
                if (grecaptcha.getResponse()) {
                  document.body.querySelector("form[name='post']").submit();
                }
                else {
                    let captcha_elt = document.getElementById("g-recaptcha");

                    if (captcha_elt) {
                        captcha_elt.style.border = "5px solid red";
                    }
                }
                `;
            document.head.appendChild(newScript);
        });

        window.addEventListener('message', function(event) {
            if (event.data && event.data.message === "dark-flow:post-form-iframe-loaded") {
                chrome.runtime.sendMessage({message: "dark-flow:post-form-iframe-loaded"});
            }
        });

        window.postMessage({message: "dark-flow:post-form-iframe-loaded"}, "*");
    }
}


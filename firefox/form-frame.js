if (location.search && location.search.endsWith("#form")) {
    let form = document.body.querySelector("form[name='post']");
    if (form) {
        document.body.innerHTML = "";

        let postForm = form.querySelector("#postForm");
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

        let newScript = document.createElement('script');
        newScript.setAttribute("async", "false");
        newScript.innerHTML = 'initRecaptcha()';
        document.head.appendChild(newScript);

        form.addEventListener("submit", (e) => {
            e.preventDefault();
            let newScript = document.createElement('script');
            newScript.innerHTML = `
                if (grecaptcha.getResponse()) {    
                    window.postMessage({message: "dark-flow:post-form-recaptcha-filled"}, "*");
                }
                else {
                    //window.postMessage({message: "dark-flow:post-form-recaptcha-filled"}, "*");
                    
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
                browser.runtime.sendMessage({message: "dark-flow:post-form-iframe-loaded"});
            }
            else if (event.data && event.data.message === "dark-flow:post-form-recaptcha-filled") {
                browser.runtime.sendMessage({message: "dark-flow:post-form-iframe-submitted"});
                form.submit();
            }
        });

        window.postMessage({message: "dark-flow:post-form-iframe-loaded"}, "*");
    }
}


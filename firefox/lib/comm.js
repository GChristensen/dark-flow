// Dark Flow
// 
// (C) 2013 g/christensen (gchristnsn@gmail.com)

function get_pages(cons_xhr, port, prefix, data)
{
    if (data.payload.length > 0)
    {
        var responses = new Array(data.payload.length);

        function check_responses()
        {
            var ready = true;
            var has_data = false;

            for (var i = 0; i < responses.length; i++) 
            {
                if (!responses[i])
                    ready = false;
                else if (responses[i].error == "success")
                    has_data = true;
            }

            if (ready)
                port.emit(data.message, 
                          {state: has_data? "ok": "error",
                           pages: responses});
        }

        for (var p = 0; p < data.payload.length; p++) 
        {
            (function () 
             {
                 var n = new Number(p);

                 var req = cons_xhr();
                 req.onload = function() 
                 {
                     if (this.status < 400)
                         responses[n] = {error: "success", 
                                         url: data.payload[n],
                                         text: this.responseText,
                                         cookie: this.getResponseHeader("Set-Cookie"),
                                         index: n};
                     else
                         responses[n] = {error: "http_error", 
                                         url: data.payload[n],
                                         status: this.status};
                     check_responses(this);
                 }
                 req.ontimeout = function() {
                     responses[n] = {error: "error"}
                     check_responses(this);
                 };
                 req.onerror = function() {
                     responses[n] = {error: "error"}
                     check_responses(this);
                 };
                 req.onabort = function() {
                     responses[n] = {error: "error"};
                     check_responses(this);
                 };

                 req.open("get", prefix + data.payload[n]);
                 req.responseType = "text";
                 req.send();
             })();
        }
    }
    else
        port.emit(data.message, {state: "error"});
}

function build_multipart_body(o)
{
    var boundary = '---------------------------';
    boundary += Math.floor(Math.random()*32768);
    boundary += Math.floor(Math.random()*32768);
    boundary += Math.floor(Math.random()*32768);

    var body = '';
    
    for (k in o)
    {
        var item = o[k];
        if (Array.isArray(item)) item = item[0];

//if (typeof(item) == 'string')
//  console.error(k + ": " + item);

        if (typeof(item) == 'string')
            body += '--' + boundary
                  + '\r\nContent-Disposition: form-data; name="' + k + '"'
                  + '\r\n\r\n' + unescape(encodeURIComponent(item)) + '\r\n';
        else
            body += '--' + boundary
                  + '\r\nContent-Disposition: form-data; name="' + k + '"; ' 
                     + 'filename="' + item.name + '"'
                  + '\r\nContent-Type: ' + item.type
                  + '\r\nContent-Transfer-Encoding: binary'
                  + '\r\n\r\n' + item.data + '\r\n';
    }

    body += '--' + boundary + '--'

    return {boundary: boundary, body: body};
}

function post_form(cons_xhr, port, prefix, data)
{
    var req = cons_xhr();
    req.onload = function() 
    {
        port.emit(data.message, {state: "ok",
                                 status: this.status,
                                 text: this.responseText,
                                 location: this.getResponseHeader("Location")});
    }
    req.onerror = function(e) {
        port.emit(data.message, {state: "error"});
    };
    req.ontimeout = function(e) {
        port.emit(data.message, {state: "timeout"});
    };
    req.onabort = function(e) {
        port.emit(data.message, {state: "error"});
    };

    multipart = build_multipart_body(data.payload.form);

    req.open("POST", prefix + data.payload.url);
    if (data.payload.timeout)
        req.timeout = data.payload.timeout;
    if (data.payload.referer)
        req.setRequestHeader("Referer", data.payload.referer);

    var bytes = [];

    for (var i = 0; i < multipart.body.length; ++i) 
        bytes.push(multipart.body.charCodeAt(i));
        
    req.setRequestHeader("Content-Type", "multipart/form-data; boundary=" + multipart.boundary);
    req.send(Uint8Array.from(bytes));
}

exports.get_pages = get_pages;
exports.post_form = post_form;
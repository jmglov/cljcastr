# cljcastr

A Zencastr clone in ClojureScript. OK, maybe not really, but more like an
exploration of the browser's audio / video APIs using ClojureScript.

See https://jmglov.net/blog/2024-02-22-cljcastr.html for more details if you
must.

If you want to watch me talk about cljcastr for some reason (you have too much
time on your hands and a penchant for punishment, perhaps?), you can check out
the [REPL-driving the browser](https://youtu.be/WIuE1uMuX3g?si=cW3eT8R4etOkdYz4)
talk I gave at [Func Prog Sweden](https://www.youtube.com/@FuncProgSweden). My
[slides are here](https://pitch.com/v/repl-driving-the-browser-mupami), lovingly
crafted with [Pitch](https://pitch.com/about), the finest online presentation
tool there is, and written in Clojure and ClojureScript to boot! 💜

And if you want to purchase your very own [Babashka](https://babashka.org/) tee
shirt, you can find it
[here](https://www.etsy.com/listing/1241766068/babashka-clj-kondo-nbb-shirt). I
would encourage you to put a piece of duct tape or a SCI sticker or something
over the [nbb](https://github.com/babashka/nbb) logo on the back. 😜

## Local development

``` text
$ bb dev --http-port 8080 --nrepl-port 1337 --websocket-port 1338

Starting webserver on port 8080 with root public
Starting nrepl server on port 1337 and websocket server on port 1338
Serving assets at http://localhost:8080
Serving static assets at http://localhost:8080
nREPL server started on port 1337...
Websocket server started on 1338...
```

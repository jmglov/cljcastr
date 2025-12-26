# cljcastr

A Zencastr clone in ClojureScript. OK, maybe not really, but more like a
set of tools for producing and self-hosting podcasts.

## Project Principles

1. **No surveillance. Ever.** cljcastr runs on your computer and only your
   computer. It does not connect to any external services, store data on the
   cloud (AKA other people's computers), or track you in any way, shape, or
   form.
2. **No AI.** All cljcastr code is written by hand, by humans. Nothing in this
   repository has been AI generated, and no AI generated pull requests will even
   be considered for inclusion.

cljcastr is hosted on Codeberg at
[jmglov/cljcastr](https://codeberg.org/jmglov/cljcastr). It is mirrored for the
time being on Github, but you should really be including it like this:

``` clojure
{:deps {jmglov/cljcastr
        {:git/url "https://codeberg.org/jmglov/cljcastr"
         #_"Use the latest SHA below"
         :git/sha "b23ffdf59cc89c6309c521c3571dceb2b1db5866"}}}
```

## Licence

cljcastr is part of the Politechs Project, and is provided under the
[Attribution-NonCommercial-NoAI-NoSaaS
1.0](https://politechs.dev/licence-by-nc-nai-ns.html) licence. Basically, you
can do what you want with this code, provided:
- You give appropriate credit to the Politechs Project
  <politechs@politechs.dev>, provide a link to [the
  license](https://politechs.dev/legalcode-by-nc-nai-ns.html), and indicate if
  changes were made. You may do so in any reasonable manner, but not in any way
  that suggests we endorse you or your use.
- You don't use it for commercial purposes.
- You don't use it for training any AI system. If you are working on an ethical
  AI project, email <politechs@politechs.dev> to ask for an exemption.
- You don't use it in any sort of Software as a Service offering. Basically, Bag
  O'Maps is for your own use, either privately or for an organisation you're a
  member of. You must not provide it to others.
- You don't add any legal terms or technological measures that legally restrict
  others from doing anything the license permits.

See the [LICENSE](LICENSE) file for the full legal text of the licence.

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

## Further reading

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

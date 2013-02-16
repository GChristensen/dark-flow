default: all

all:
	cp ../firefox/lib/comm.js ./war/comm.js
	cp ../firefox/data/init_page.js ./war/init_page.js
	cp ../clojure/frontend.js ./war/frontend.js
	cp -r ../themes ./war

clean:
	rm ./war/comm.js
	rm ./war/init_page.js
	rm ./war/frontend.js
	rm -r ./war/themes

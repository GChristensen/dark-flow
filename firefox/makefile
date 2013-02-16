
default: all

all:
	cd ../clojure; lein with-profile production cljsbuild once
	cp ../clojure/frontend.js ./data/frontend.js
	cp -r ../themes ./data
	activate; cd "$(CURDIR)"; cfx xpi

clean:
	rm ./dark-flow-aggregator.xpi
	rm ./data/frontend.js
	rm -r ./data/themes

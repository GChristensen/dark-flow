default: all

debug:
	lein with-profile debug cljsbuild once

all:
	lein with-profile production cljsbuild once

clean:
	rm -r ./target
	rm ./frontend.js
	rm ../firefox/engine/frontend.js

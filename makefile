.PHONY: test
test:
	cd firefox; start web-ext run -p "%APPDATA%/Mozilla/Firefox/Profiles/debug" --keep-profile-changes --browser-console

.PHONY: sign
sign:
	cd firefox; web-ext sign -i web-ext-artifacts updates.json `cat $(HOME)/.amo/creds`

.PHONY: clojure
clojure:
	cd clojure; make

.PHONY: clojure-debug
clojure-debug:
	cd clojure; make debug

.PHONY: chrome
chrome:
	rm -f DarkFlow.zip
	cd firefox; 7za a ../DarkFlow.zip * -xr!web-ext-artifacts/*

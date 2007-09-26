# SGS Makefile

TOPDIR = .

VERSION = 0.9.3

DISTNAME = sgs_c_client_$(VERSION)

DIST =  $(DISTNAME).zip \
	$(DISTNAME).tar.gz

all: build

dist: $(DIST)

build: build_src build_test build_example

build_src: 
	cd $(TOPDIR)/src/client/c && $(MAKE)

build_test: build_src
	cd $(TOPDIR)/test/client/c && $(MAKE)

build_example: build_src
	cd $(TOPDIR)/example/c-chat && $(MAKE)

build_dist:
	-rm -rf $(DISTNAME)
	@mkdir -p $(DISTNAME)
	@mkdir -p $(DISTNAME)/etc
	@mkdir -p $(DISTNAME)/src/client
	@mkdir -p $(DISTNAME)/test/client
	@mkdir -p $(DISTNAME)/example
	svn -q export $(TOPDIR)/src/client/c $(DISTNAME)/src/client/c
	svn -q export $(TOPDIR)/test/client/c $(DISTNAME)/test/client/c
	svn -q export $(TOPDIR)/example/c-chat $(DISTNAME)/example/c-chat
	svn -q export $(TOPDIR)/etc/mk $(DISTNAME)/etc/mk
	svn -q export $(TOPDIR)/GNUmakefile $(DISTNAME)/GNUmakefile
	svn -q export $(TOPDIR)/env.bat $(DISTNAME)/env.bat
	svn -q export $(TOPDIR)/etc/CHANGELOG-client $(DISTNAME)/CHANGELOG
	svn -q export $(TOPDIR)/etc/LICENSE-client $(DISTNAME)/LICENSE
	svn -q export $(TOPDIR)/etc/NOTICE-client-doc.txt $(DISTNAME)/NOTICE.txt
	cat $(TOPDIR)/etc/README-client-c-source | \
	    sed 's/@VERSION@/$(VERSION)/g' > $(DISTNAME)/README

clean:
	@cd $(TOPDIR)/src/client/c && $(MAKE) $@
	@cd $(TOPDIR)/test/client/c && $(MAKE) $@
	@cd $(TOPDIR)/example/c-chat && $(MAKE) $@

realclean:
	-rm -rf $(DISTNAME)
	-rm -f $(DIST)
	@cd $(TOPDIR)/src/client/c && $(MAKE) $@
	@cd $(TOPDIR)/test/client/c && $(MAKE) $@
	@cd $(TOPDIR)/example/c-chat && $(MAKE) $@

$(DISTNAME).zip: build_dist
	$(ZIP) -r9q $@ $(DISTNAME)

$(DISTNAME).tar.gz: build_dist
	$(TAR) cf - $(DISTNAME) | $(GZIP) -c > $@

.PHONY: all build build_src build_test build_example dist

include $(TOPDIR)/etc/mk/sgs.mk


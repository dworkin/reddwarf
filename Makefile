# SGS Makefile

TOPDIR = .

DISTNAME = sgs_c_api_$(VERSION)
DIST = \
	$(DISTDIR)/$(DISTNAME).zip \
	$(DISTDIR)/$(DISTNAME).tar.gz

all: $(DIST)

build: build_src build_test build_example

build_src: 
	cd $(TOPDIR)/src/client/c && $(MAKE)

build_test: 
	cd $(TOPDIR)/test/client/c && $(MAKE)

build_example: 
	cd $(TOPDIR)/example/c-chat && $(MAKE)

dist: build_src build_example

clean:
	cd $(TOPDIR)/src/client/c && $(MAKE) $@
	cd $(TOPDIR)/test/client/c && $(MAKE) $@
	cd $(TOPDIR)/example/c-chat && $(MAKE) $@

realclean:
	cd $(TOPDIR)/src/client/c && $(MAKE) $@
	cd $(TOPDIR)/test/client/c && $(MAKE) $@
	cd $(TOPDIR)/example/c-chat && $(MAKE) $@

$(DISTDIR)/$(DISTNAME).zip: dist
	@mkdir -p $(DISTDIR)

$(DISTDIR)/$(DISTNAME).tar.gz: dist
	@mkdir -p $(DISTDIR)

.PHONY: all build build_src build_test build_example dist

include $(TOPDIR)/etc/mk/sgs.mk


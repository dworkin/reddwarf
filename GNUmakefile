# RedDwarf Example C Client Makefile

TOPDIR=.

SRCS = \
	sgs/linked_map.c \
	sgs/buffer.c \
	sgs/io_utils.c \
	sgs/message.c \
	sgs/id_impl.c \
	sgs/connection.c \
	sgs/context.c \
	sgs/session.c \
	sgs/channel.c \
	sgs/socket.c

LIB = libsgsclient.a

VERSION = 0.10.2

DIST_FILE = sgs-c-client-dist-$(VERSION).zip

DIST_DIR = sgs-c-client-$(VERSION)

all: $(LIB)

include $(TOPDIR)/etc/sgs.mk

$(LIB): $(OBJS)
	$(AR) $@ $(OBJS)

dist:
	-/bin/rm -rf $(TOPDIR)/target/$(DIST_DIR) $(TOPDIR)/target/$(DIST_FILE)
	-mkdir -p $(TOPDIR)/target/$(DIST_DIR)
	-cp GNUmakefile AUTHORS LICENSE NOTICE.txt README CHANGELOG $(TOPDIR)/target/$(DIST_DIR)
	-mkdir $(TOPDIR)/target/$(DIST_DIR)/etc
	-cp etc/*.mk etc/*.bat $(TOPDIR)/target/$(DIST_DIR)/etc
	-mkdir $(TOPDIR)/target/$(DIST_DIR)/sgs
	-cp sgs/*.c sgs/*.h $(TOPDIR)/target/$(DIST_DIR)/sgs
	-mkdir $(TOPDIR)/target/$(DIST_DIR)/sgs/private
	-cp sgs/private/*.h $(TOPDIR)/target/$(DIST_DIR)/sgs/private
	-mkdir $(TOPDIR)/target/$(DIST_DIR)/test
	-cp test/GNUmakefile test/*.c test/*.mak test/*.h test/*.txt \
	    $(TOPDIR)/target/$(DIST_DIR)/test
	-cd $(TOPDIR)/target; zip -r $(DIST_FILE) $(DIST_DIR)

clean:
	-/bin/rm -rf $(OBJDIR)/sgs $(TOPDIR)/target
	-@cd $(TOPDIR)/test && $(MAKE) $@

realclean: clean
	-/bin/rm -f $(LIB)
	-@cd $(TOPDIR)/test && $(MAKE) $@

# Project Darkstar Example C Client Makefile

TOPDIR=.

SRCS = \
	sgs/hex_utils.c \
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

all: $(LIB)

include $(TOPDIR)/etc/sgs.mk

$(LIB): $(OBJS)
	$(AR) $@ $(OBJS)

clean:
	-/bin/rm -rf $(OBJDIR)
	-@cd $(TOPDIR)/test && $(MAKE) $@
	-@cd $(TOPDIR)/chatclient && $(MAKE) $@

realclean: clean
	-/bin/rm -f $(LIB)
	-@cd $(TOPDIR)/test && $(MAKE) $@
	-@cd $(TOPDIR)/chatclient && $(MAKE) $@


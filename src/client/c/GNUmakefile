# SGS C Client Makefile

TOPDIR=../../..

SRCS = \
	sgs/hex_utils.c \
	sgs/private/linked_map.c \
	sgs/buffer.c \
	sgs/private/io_utils.c \
	sgs/message.c \
	sgs/compact_id.c \
	sgs/connection.c \
	sgs/context.c \
	sgs/session.c \
	sgs/channel.c

LIB = libsgsclient.a

all: $(LIB)

include $(TOPDIR)/etc/mk/sgs.mk

$(LIB): $(OBJS)
	$(AR) $@ $(OBJS)

clean: clean.default

realclean: realclean.default
	@/bin/rm -f $(LIB)


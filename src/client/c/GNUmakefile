# SGS C Client Makefile

TOPDIR=../../..

SRCS = \
	sgs/buffer.c \
	sgs/compact_id.c \
	sgs/context.c \
	sgs/connection.c \
	sgs/hex_utils.c \
	sgs/message.c

LIB = libsgsclient.a

all: $(LIB)

include $(TOPDIR)/etc/mk/sgs.mk

$(LIB): $(OBJS)
	$(AR) $@ $(OBJS)

clean: clean.default

realclean: realclean.default
	@/bin/rm -f $(LIB)


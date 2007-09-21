# SGS C Client Makefile

TOPDIR=../../..

SRCS = \
	impl/sgs_buffer_impl.c \
	impl/sgs_channel_impl.c \
	impl/sgs_compact_id.c \
	impl/sgs_connection_impl.c \
	impl/sgs_context_impl.c \
	impl/sgs_linked_list_map.c \
	impl/sgs_hex_utils.c \
	impl/sgs_message.c \
	impl/sgs_session_impl.c

LIB = libsgsclient.a

all: $(LIB)

include $(TOPDIR)/etc/mk/sgs.mk

$(LIB): $(OBJS)
	$(AR) $@ $(OBJS)

clean: clean.default

realclean: realclean.default
	@/bin/rm -f $(LIB)


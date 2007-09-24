# SGS C Client Tests Makefile

TOPDIR=../../..

SRCS = \
	sgs_buffer_test.c \
	sgs_id_test.c \
	sgs_map_test.c \
	sgs_message_test.c

#all: buffertest idtest maptest messagetest
all: buffertest idtest messagetest

include $(TOPDIR)/etc/mk/sgs.mk

INCS += -I$(TOPDIR)/src/client/c
LDFLAGS += -L$(TOPDIR)/src/client/c
LIBS += -lsgsclient

buffertest: $(OBJDIR)/sgs_buffer_test.o
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $< $(LIBS) $(LDLIBS)

idtest: $(OBJDIR)/sgs_id_test.o
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $< $(LIBS) $(LDLIBS)

maptest: $(OBJDIR)/sgs_map_test.o
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $< $(LIBS) $(LDLIBS)

messagetest: $(OBJDIR)/sgs_message_test.o
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $< $(LIBS) $(LDLIBS)

clean: clean.default

realclean: realclean.default
	@/bin/rm -f buffertest idtest maptest messagetest


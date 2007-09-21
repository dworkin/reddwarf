# SGS C Client Chat Example Makefile

TOPDIR=../..

SRCS = sgs_chat_client.c

all: chatclient

include $(TOPDIR)/etc/mk/sgs.mk

INCS += -I$(TOPDIR)/src/client/c
LDFLAGS += -L$(TOPDIR)/src/client/c
LIBS += -lsgsclient

chatclient: $(OBJS)
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $(OBJS) $(LIBS) $(LDLIBS)

clean: clean.default

realclean: realclean.default
	@/bin/rm -f chatclient


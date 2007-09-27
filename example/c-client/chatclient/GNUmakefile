# SGS C Client Chat Example Makefile

TOPDIR=../..

SRCS = chat_client.c

EXE = chat_client

all: $(EXE)

include $(TOPDIR)/etc/mk/sgs.mk

#DBG += -g
INCS += -I$(TOPDIR)/src/client/c
LDFLAGS += -L$(TOPDIR)/src/client/c
LIBS += -lsgsclient

$(EXE): $(OBJS)
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $(OBJS) $(LIBS) $(LDLIBS)

clean: clean.default

realclean: realclean.default
	/bin/rm -f $(EXE)


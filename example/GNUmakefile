# Project Darkstar Example C Chat Client Makefile

TOPDIR=../

SRCS = chat_client.c

EXE = chat_client

all: $(EXE)

include $(TOPDIR)/etc/sgs.mk

DBG += -g
INCS += -I$(TOPDIR)
CFLAGS += -Wno-unused
LDFLAGS += -L$(TOPDIR)
LIBS += -lsgsclient

$(EXE): $(OBJS)
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $(OBJS) $(LIBS) $(LDLIBS)

clean:
	-/bin/rm -rf $(OBJDIR)/*

realclean: clean
	-/bin/rm -f $(EXE)


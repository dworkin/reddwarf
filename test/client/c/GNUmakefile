# SGS C Client Tests Makefile

TOPDIR=../../..

SRCS = \
	buffer_test.c \
	id_test.c \
	map_test.c \
	message_test.c

EXES = buffer_test id_test map_test message_test

all: $(EXES)

include $(TOPDIR)/etc/mk/sgs.mk

DBG += -g
INCS += -I$(TOPDIR)/src/client/c
LDFLAGS += -L$(TOPDIR)/src/client/c
LIBS += -lsgsclient

%_test: $(OBJDIR)/%_test.o
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $< $(LIBS) $(LDLIBS)

clean: clean.default

realclean: realclean.default
	/bin/rm -f $(EXES)


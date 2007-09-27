# SGS C Client Tests Makefile

TOPDIR=..

SRCS = \
	buffer_test.c \
	id_test.c \
	map_test.c \
	message_test.c

EXES = buffer_test id_test map_test message_test

all: $(EXES)

include $(TOPDIR)/etc/sgs.mk

#DBG += -g
INCS += -I$(TOPDIR)
LDFLAGS += -L$(TOPDIR)
LIBS += -lsgsclient

%_test: $(OBJDIR)/%_test.o
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $< $(LIBS) $(LDLIBS)

clean:
	-/bin/rm -rf $(OBJDIR)

realclean: clean
	-/bin/rm -f $(EXES)


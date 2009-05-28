# Project Darkstar Example C Client Tests Makefile

TOPDIR=..

SRCS = \
	buffer_test.c \
	id_test.c \
	map_test.c \
	message_test.c\
	smokeTestClient.c

EXES = buffer_test id_test map_test message_test smokeTestClient

all: $(EXES)

include $(TOPDIR)/etc/sgs.mk

#DBG += -g
INCS += -I$(TOPDIR)
CFLAGS += -Wno-unused
LDFLAGS += -L$(TOPDIR)
LIBS += -lsgsclient

%_test: $(OBJDIR)/%_test.o
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $< $(LIBS) $(LDLIBS)

smokeTestClient : smokeTestClient.c testCallbacks.o
	$(CC) $(CFLAGS) $(LDFLAGS) -g -o $@ $<  testCallbacks.o $(LIBS) $(LDLIBS)

testCallbacks.o : testCallbacks.c testCallbacks.h
		$(CC) $(CFLAGS) -g  -c testCallbacks.c 
clean:
	-/bin/rm -rf $(OBJDIR)/*.o

realclean: clean
	-/bin/rm -f $(EXES)


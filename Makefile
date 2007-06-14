#
#
OS = $(shell uname)

CC = gcc
CFLAGS = -Wall -O -std=c99 -pedantic

ifeq ($(OS),SunOS)
  LINKFLAGS = -lnsl -lsocket
else
  LINKFLAGS = 
endif

ODIR = obj
SRCDIR = src/client/c
MAIN_EXE = bin/SimpleChatClient

DEPS = $(wildcard $(SRCDIR)/*.h)
SRCS = Channels.c CompactId.c HexUtils.c MessageProtocol.c ServerSession.c
OBJS = $(addprefix $(ODIR)/, $(SRCS:.c=.o))

.PHONY:	clean tar run

$(ODIR)/%.o: $(SRCDIR)/%.c $(DEPS)
	@mkdir -p obj
	$(CC) $(CFLAGS) -c $< -o $@

all: $(MAIN_EXE)

$(MAIN_EXE): client

client:	$(OBJS) $(DEPS) $(ODIR)/ChatClientImpl.o
	@mkdir -p bin
	$(CC) $(CFLAGS) $(LINKFLAGS) -o $(MAIN_EXE) $(OBJS) $(ODIR)/ChatClientImpl.o

tar: client
	mv -f c_backups.tar c_backups.tar.prev
	tar cf c_backups.tar $(SRCDIR)

clean:
	rm -f *~ core callbacks.out $(SRCDIR)/*~ $(ODIR)/*.o $(SRCDIR)/*.gch

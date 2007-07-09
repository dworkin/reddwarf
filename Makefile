#
#
OS = $(shell uname)
CC = gcc

ifeq ($(OS),SunOS)
  CFLAGS = -Wall -O -std=c99 -pedantic -D __EXTENSIONS__
  LINKFLAGS = -lnsl -lsocket
else
  CFLAGS = -Wall -O -std=c99 -pedantic
  LINKFLAGS =
endif

ODIR = obj
BINDIR = bin
SRCDIR = src/client/c

API_HEADERS = $(wildcard $(SRCDIR)/*.h)
SRCS = $(wildcard $(SRCDIR)/*.c) $(wildcard $(SRCDIR)/*/*.c)
OLDSRCS = Channel.c CompactId.c HexUtils.c MessageProtocol.c ServerSession.c SessionCallbacks.c
OBJS = $(addprefix $(ODIR)/, $(SRCS:.c=.o))

# automatic variable reminders:
#  $@ = rule target
#  $< = first prerequisite
#  $? = all prerequisites that are newer than the target
#  $^ = all prerequisites

.PHONY:	clean tar run

all:
	@echo "'all' target not yet implemented in Makefile..."

$(ODIR)/%.o: $(SRCDIR)/%.c $(API_HEADERS) $(SRCDIR)/%.h
	@mkdir -p $(ODIR)
	$(CC) $(CFLAGS) -I $(SRCDIR) -c $< -o $@

$(ODIR)/example/%.o: $(SRCDIR)/example/%.c $(API_HEADERS)
	@mkdir -p $(ODIR)/example
	$(CC) $(CFLAGS) -I $(SRCDIR) -c $< -o $@

$(ODIR)/impl/%.o: $(SRCDIR)/impl/%.c $(API_HEADERS) $(SRCDIR)/impl/%.h
	@mkdir -p $(ODIR)/impl
	$(CC) $(CFLAGS) -I $(SRCDIR) -c $< -o $@

$(ODIR)/test/%.o: $(SRCDIR)/test/%.c $(API_HEADERS)
	@mkdir -p $(ODIR)/test
	$(CC) $(CFLAGS) -I $(SRCDIR) -I $(SRCDIR)/impl -c $< -o $@

chatclient: $(ODIR)/example/sgs_chat_client.o $(DEPS) $(ODIR)/impl/sgs_connection_impl.o $(ODIR)/impl/sgs_context_impl.o $(ODIR)/impl/sgs_session_impl.o $(ODIR)/sgs_id.o $(ODIR)/sgs_buffer.o $(ODIR)/sgs_message.o $(ODIR)/sgs_hex_utils.o $(ODIR)/impl/sgs_linked_list_map.o $(ODIR)/impl/sgs_channel_impl.o
	@mkdir -p $(BINDIR)
	$(CC) $(CFLAGS) $(LINKFLAGS) -o $(BINDIR)/chatclient $^

buffertest: $(ODIR)/sgs_buffer.o $(ODIR)/test/sgs_buffer_test.o
	@mkdir -p $(BINDIR)
	$(CC) $(CFLAGS) $(LINKFLAGS) -o $(BINDIR)/buffertest $^

idtest: $(ODIR)/sgs_id.o $(ODIR)/test/sgs_id_test.o $(ODIR)/sgs_hex_utils.o
	@mkdir -p $(BINDIR)
	$(CC) $(CFLAGS) $(LINKFLAGS) -o $(BINDIR)/idtest $^

maptest: $(ODIR)/impl/sgs_linked_list_map.o $(ODIR)/test/sgs_map_test.o
	@mkdir -p $(BINDIR)
	$(CC) $(CFLAGS) $(LINKFLAGS) -o $(BINDIR)/maptest $^

messagetest: $(ODIR)/sgs_message.o $(ODIR)/test/sgs_message_test.o
	@mkdir -p $(BINDIR)
	$(CC) $(CFLAGS) $(LINKFLAGS) -o $(BINDIR)/messagetest $^

tests: buffertest idtest maptest messagetest

tar:
	mv -f c_backups.tar c_backups.tar.prev
	tar cf c_backups.tar $(SRCDIR)

clean:
	rm -f *~ core callbacks.out $(SRCDIR)/*~ $(SRCDIR)/*/*~ $(ODIR)/*.o $(ODIR)/*/*.o $(SRCDIR)/*.gch $(SRCDIR)/*/*.gch

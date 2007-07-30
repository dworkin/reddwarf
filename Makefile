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

# directories to look in / put things
BINDIR  = bin
DISTDIR = dist
ODIR    = obj
SRCDIR  = src/client/c

# the base name (sans suffix) of the distribution file to create ("make tar") or ("make zip")
DISTFILE = sgs_c_api

HEADERS = $(wildcard $(SRCDIR)/*.h) $(wildcard $(SRCDIR)/impl/*.h)
SRCS = $(notdir $(wildcard $(SRCDIR)/*.c)) $(addprefix impl/, $(notdir $(wildcard $(SRCDIR)/impl/*.c)))
OBJS = $(addprefix $(ODIR)/, $(SRCS:.c=.o))

# automatic variable reminders:
#  $@ = rule target
#  $< = first prerequisite
#  $? = all prerequisites that are newer than the target
#  $^ = all prerequisites

.PHONY:	clean tar zip

chatclient: $(OBJS) $(ODIR)/example/sgs_chat_client.o
	@mkdir -p $(BINDIR)
	$(CC) $(CFLAGS) $(LINKFLAGS) -o $(BINDIR)/chatclient $^

$(ODIR)/%.o: $(SRCDIR)/%.c $(HEADERS)
	@mkdir -p $(dir $@)
	$(CC) $(CFLAGS) -I $(SRCDIR) -I $(SRCDIR)/impl -c $< -o $@

buffertest: $(ODIR)/impl/sgs_buffer_impl.o $(ODIR)/test/sgs_buffer_test.o
	@mkdir -p $(BINDIR)
	$(CC) $(CFLAGS) $(LINKFLAGS) -o $(BINDIR)/buffertest $^

idtest: $(ODIR)/impl/sgs_compact_id.o $(ODIR)/test/sgs_id_test.o $(ODIR)/sgs_hex_utils.o
	@mkdir -p $(BINDIR)
	$(CC) $(CFLAGS) $(LINKFLAGS) -o $(BINDIR)/idtest $^

maptest: $(ODIR)/impl/sgs_linked_list_map.o $(ODIR)/test/sgs_map_test.o
	@mkdir -p $(BINDIR)
	$(CC) $(CFLAGS) $(LINKFLAGS) -o $(BINDIR)/maptest $^

messagetest: $(ODIR)/sgs_message.o $(ODIR)/impl/sgs_compact_id.o $(ODIR)/sgs_hex_utils.o $(ODIR)/test/sgs_message_test.o
	@mkdir -p $(BINDIR)
	$(CC) $(CFLAGS) $(LINKFLAGS) -o $(BINDIR)/messagetest $^

all: chatclient tests

tests: buffertest idtest maptest messagetest

tar: chatclient
	@mkdir -p $(DISTDIR)
	rm -f $(DISTDIR)/$(DISTFILE).tar
	tar cvf $(DISTDIR)/$(DISTFILE).tar README.txt Makefile $(HEADERS) $(addprefix $(SRCDIR)/, $(SRCS)) $(SRCDIR)/example/sgs_chat_client.c

zip: chatclient
	@mkdir -p $(DISTDIR)
	rm -f $(DISTDIR)/$(DISTFILE).zip
	zip -v $(DISTDIR)/$(DISTFILE).zip README.txt Makefile $(HEADERS) $(addprefix $(SRCDIR)/, $(SRCS)) $(SRCDIR)/example/sgs_chat_client.c

clean:
	rm -f *~ core callbacks.out $(SRCDIR)/*~ $(SRCDIR)/*/*~ $(SRCDIR)/*.gch $(SRCDIR)/*/*.gch
	rm -fr $(ODIR)

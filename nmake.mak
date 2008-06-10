# Project Darkstar Example C Client Makefile

TOPDIR=.

!include $(TOPDIR)\etc\nmake.mk

OBJS = \
	$(OBJDIR)\hex_utils.obj \
	$(OBJDIR)\linked_map.obj \
	$(OBJDIR)\buffer.obj \
	$(OBJDIR)\io_utils.obj \
	$(OBJDIR)\message.obj \
	$(OBJDIR)\compact_id.obj \
	$(OBJDIR)\context.obj \
	$(OBJDIR)\session.obj \
	$(OBJDIR)\channel.obj

# TODO: connection.c needs to be ported to the Winsock2 API,
# either in a separate implementation file or with ifdefs.
#       $(OBJDIR)\connection.obj

LIB = sgsclient.lib

all: $(LIB)

$(LIB): $(OBJS)
	lib.exe /OUT:$(LIB) $(OBJS) $(LIBS)

{sgs\}.c{$(OBJDIR)\}.obj::
	-md "$(OBJDIR)"
	$(CC) $(CFLAGS) /Fo"$(OBJDIR)"\ -c $<

clean:
	-rd /s /q "$(OBJDIR)"

realclean: clean
	-del /q $(LIB)

# SGS C Client Makefile

TOPDIR=..\..\..

!include $(TOPDIR)\etc\mk\nmake.mk

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

# TODO
# obj\connection.obj

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

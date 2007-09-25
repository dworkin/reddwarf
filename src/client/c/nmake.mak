# SGS C Client Makefile

TOPDIR=..\..\..

!include $(TOPDIR)\etc\mk\nmake.mk

OBJS = \
	obj\hex_utils.obj \
	obj\linked_map.obj \
	obj\buffer.obj \
	obj\io_utils.obj \
	obj\message.obj \
	obj\compact_id.obj \
	obj\context.obj \
	obj\session.obj \
	obj\channel.obj

# TODO
# obj\connection.obj

LIB = sgsclient.lib

all: $(LIB)

$(LIB): $(OBJS)
    lib.exe /OUT:$(LIB) $(OBJS) $(LIBS)

{sgs\}.c{obj\}.obj::
    $(CC) $(CFLAGS) /Foobj\ -c $<

clean:
    rd /s /q obj

realclean: clean
    del /q $(LIB)
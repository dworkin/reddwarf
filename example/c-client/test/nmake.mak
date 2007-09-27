# Project Darkstar Example C Client Tests Makefile

TOPDIR=..

!include $(TOPDIR)\etc\nmake.mk

EXES = \
        buffer_test.exe \
        id_test.exe \
        map_test.exe \
        message_test.exe

all: $(EXES)

INCS = $(INCS) /I$(TOPDIR)
LDFLAGS = /LIBPATH:$(TOPDIR) $(LDFLAGS)
LIBS = sgsclient.lib $(LIBS)

.c.exe:
	-md "$(OBJDIR)"
	$(CC) $(CFLAGS) /Fo"$(OBJDIR)"\ $< /link $(LDFLAGS) $(LIBS)

clean:
	-rd /s /q "$(OBJDIR)"

realclean: clean
	-del /q $(EXES)

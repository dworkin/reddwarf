# Include file for SGS NMake Makefile

VERSION=0.9.3

BINDIR  = $(TOPDIR)\bin
DISTDIR = $(TOPDIR)\dist
OBJDIR  = obj

VCDIR  = "C:\Program Files\Microsoft Visual Studio 8\VC"
SDKDIR = "C:\Program Files\Microsoft Platform SDK for Windows Server 2003 R2"

CC=cl
DBG = /W3
#DBG = /W3 /Zi /Od
OPT = /O2
DEFS = /DWIN32 /D_WIN32
INCS = /I$(VCDIR)\include /I$(SDKDIR)\include /I.
CFLAGS = /nologo $(DBG) $(OPT) $(DEFS) $(INCS) 
LDFLAGS = /LIBPATH:$(VCDIR)\lib /LIBPATH:$(SDKDIR)\lib
LIBS = WS2_32.lib

.SUFFIXES: .obj


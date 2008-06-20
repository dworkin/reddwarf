# Include file for SGS NMake Makefile

BINDIR  = $(TOPDIR)\bin
OBJDIR  = obj

VCDIR  = "C:\Program Files (x86)\Microsoft Visual Studio .NET 2003\VC7"
SDKDIR = "C:\Program Files (x86)\Microsoft Visual Studio .NET 2003\VC7\PlatformSDK"

CC=cl
DBG = /W3
#DBG = /W3 /Zi /Od
OPT = /O2
DEFS = /DWIN32 /D_WIN32
INCS = /I$(VCDIR)\include /I$(SDKDIR)\include /I.
CFLAGS = /nologo /TC $(DBG) $(OPT) $(DEFS) $(INCS) 
LDFLAGS = /LIBPATH:$(VCDIR)\lib /LIBPATH:$(SDKDIR)\lib
LIBS = WS2_32.lib

.SUFFIXES: .obj


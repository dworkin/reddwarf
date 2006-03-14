!IF "$(CFG)"=="Release"

OUTDIR=.\Release
CFG_CPPFLAGS=/Od /D "_DEBUG" /Gm /RTC1 /MTd /Gy /ZI

!ELSEIF "$(CFG)"=="Debug"

OUTDIR=.\Debug
CFG_CPPFLAGS=/O2 /D "NDEBUG"           /MT      /Zi

!ELSE

!ERROR You must specify CFG=Release or CFG=Debug

!ENDIF

OBJS=$(OUTDIR)\*.obj

SRCS=.\Source\XMLParser\*.cpp .\Source\Utilities\*.cpp .\Source\Protocol\*.cpp .\Source\Discovery\*.cpp .\Source\Client\*.cpp

HEADERS=.\Source\*.h .\Source\XMLParser\*.h .\Source\Utilities\*.h .\Source\Protocol\*.h .\Source\Discovery\*.h .\Source\Client\*.h

WIN32_SRCS=.\Source\Socket\Win32\*.cpp .\Source\Platform\Win32\*.cpp

WIN32_HEADERS=.\Source\Socket\Win32\*.h # .\Source\Platform\Win32\*.h - dne

INC=".\Source"
LIBXML_INC="..\..\..\ThirdParty\libxml2-2.6.23\include"

CPPFLAGS=/I $(INC) /I $(LIBXML_INC) /D "_LIB" /D "_UNICODE" /D "UNICODE" /EHsc /GS /Zc:forScope /GR /W4 /WX /Wp64 /TP /Fo"$(OUTDIR)/" /Fd"$(OUTDIR)/vc70.pdb"
CROSS_CPPFLAGS=/Za 
WIN32_CPPFLAGS=/D "WIN32" /D "_WINDOWS" 

LIB32=lib.exe
LIB32_FLAGS=/NOLOGO

all: "$(OUTDIR)\SunGameServer.lib"

"$(OUTDIR)\SunGameServer.lib": $(SRCS) $(WIN32_SRCS) $(HEADERS) $(WIN32_HEADERS)
	$(CPP) $(CPPFLAGS) $(CROSS_CPPFLAGS) $(CFG_CPPFLAGS) /c $(SRCS)
	$(CPP) $(CPPFLAGS) $(WIN32_CPPFLAGS) $(CFG_CPPFLAGS) /c $(WIN32_SRCS)
	$(LIB32) /OUT:"$(OUTDIR)/SunGameServer.lib" $(LIB32_FLAGS) $(OBJS)
	@echo Done

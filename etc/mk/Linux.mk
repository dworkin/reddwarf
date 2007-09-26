CC=gcc
AR=ar cru
DBG = -W -Wall -std=c99 -pedantic
OPT = -O
DEFS =
INCS = -I.
CFLAGS = $(DBG) $(OPT) $(DEFS) $(INCS)
LDFLAGS = $(DBG) $(OPT)
LDLIBS =
ZIP=zip
GZIP=gzip
TAR=tar

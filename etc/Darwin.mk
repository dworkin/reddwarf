CC=gcc
AR=ar cru
DBG = -g -W -Wall -std=c99 -pedantic
OPT = -O
DEFS = -D__EXTENSIONS__
INCS = -I. 
CFLAGS = $(DBG) $(OPT) $(DEFS) $(INCS)
LDFLAGS = $(DBG) $(OPT)
LDLIBS =
ZIP=zip
GZIP=gzip
TAR=tar

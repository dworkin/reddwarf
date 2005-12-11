# A one-off Makefile for the HADB tests.
# Quite ad-hoc, for now.
#
# Daniel Ellard

CLASSPATH	= ../../../../..
OUTPATH		= /tmp/$(USER)/ds
#JAVAC		= javac -Xlint
JAVAC		= javac

build:
	mkdir -p $(OUTPATH)/class
	$(JAVAC) -d $(OUTPATH)/class -classpath $(CLASSPATH) *.java

doc:
	mkdir -p $(OUTPATH)/javadoc
	javadoc -d $(OUTPATH)/javadoc -classpath $(CLASSPATH) *.java

all:	build doc

# 
# Copyright (c) 2001, Sun Microsystems Laboratories 
# All rights reserved. 
# 
# Redistribution and use in source and binary forms, 
# with or without modification, are permitted provided 
# that the following conditions are met: 
# 
#     Redistributions of source code must retain the 
#     above copyright notice, this list of conditions 
#     and the following disclaimer. 
#             
#     Redistributions in binary form must reproduce 
#     the above copyright notice, this list of conditions 
#     and the following disclaimer in the documentation 
#     and/or other materials provided with the distribution. 
#             
#     Neither the name of Sun Microsystems, Inc. nor 
#     the names of its contributors may be used to endorse 
#     or promote products derived from this software without 
#     specific prior written permission. 
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
# CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
# INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
# DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
# OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
# PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
# ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
# OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
# THE POSSIBILITY OF SUCH DAMAGE. 
#

subdirs = util advertising allocation reliable 

VERSION=1.1

BASE='<P align="center"> <font size=-1>Java<sup>TM</sup> Reliable Multicast<sup>TM</sup> Service version $(VERSION)<br>Copyright (c) 2001, Sun Microsystems Laboratories, All rights reserved.'

WINTITLE="Java(tm) Reliable Multicast(tm) Service version $(VERSION)"

DOCTITLE="Java<sup>TM</sup> Reliable Multicast<sup>TM</sup> Service version $(VERSION)"

JAVAFLAGS = -g

###
### Notes:  Blank lines are only allowed between rules!
### 	    Commands must start with a tab and not spaces!
###

#
# Build a version of the code that is for Global consumption
#
all:
	rm -rf ../classes
	mkdir ../classes
	@for i in $(subdirs); do \
		echo "making all in $$i"; \
		(cd $$i; make all); \
	done;

#
#
# Make a jrms source kit
#
KITDIR=../Kit
KIT=$(KITDIR)/jrms1.1
JAVADOC_SRC=javadoc_src

kit:	
	rm -rf $(KITDIR)
	mkdir -p $(KIT)/javadoc
	mkdir -p $(KIT)/$(JAVADOC_SRC)/com/sun/multicast/test/msendreceive
#
# Move all the .java files and html files to a tree under 
# $(KIT)/src/com/sun/multicast so that javadoc can be invoked for the package.
#
	find . -name "*.java" -print -o -name SCCS -prune -o -name deleted_files \
		-prune -o -name Kit -prune > $(KIT)/$(JAVADOC_SRC)/files.tmp
	find . -name "*.html" -print -o -name SCCS -prune -o -name deleted_files \
		-prune -o -name Kit -prune >> $(KIT)/$(JAVADOC_SRC)/files.tmp
	/usr/bin/tar cf - -I $(KIT)/$(JAVADOC_SRC)/files.tmp | (cd $(KIT)/$(JAVADOC_SRC)/com/sun/multicast; tar xf -)
	(cd $(KIT)/$(JAVADOC_SRC); mv com/sun/multicast/reliable/transport/lrmp/inria .)
	rm $(KIT)/$(JAVADOC_SRC)/files.tmp

	(cd $(KIT)/$(JAVADOC_SRC)/com/sun/multicast/reliable; ln -s sample_code applications)

	javadoc -d $(KIT)/javadoc -bottom $(BASE) -windowtitle $(WINTITLE) -doctitle $(DOCTITLE) -sourcepath $(KIT)/$(JAVADOC_SRC) com.sun.multicast.reliable com.sun.multicast.util com.sun.multicast.reliable.transport com.sun.multicast.reliable.transport.um com.sun.multicast.reliable.transport.tram com.sun.multicast.reliable.transport.lrmp com.sun.multicast.advertising com.sun.multicast.reliable.channel com.sun.multicast.allocation com.sun.multicast.reliable.simple com.sun.multicast.reliable.applications.chat com.sun.multicast.reliable.applications.stock com.sun.multicast.reliable.applications.slingerSwing com.sun.multicast.reliable.applications.dynamic_filters com.sun.multicast.reliable.applications.mctest com.sun.multicast.reliable.applications.tree com.sun.multicast.reliable.applications.testtools inria.net inria.util inria.net.lrmp

#
# Remove temporary source files
#
	rm -rf $(KIT)/$(JAVADOC_SRC)
#
# Copy source files that we want on the kit
#
	rm -rf $(KIT)/src
	mkdir $(KIT)/src
	tar cfFF - . | (cd $(KIT)/src; tar xf -)
	rm -rf $(KIT)/src/Codemgr_wsdata
	rm -rf $(KIT)/src/deleted_files
	rm -rf $(KIT)/src/*.out
	cp ../LICENSE.TXT $(KIT)
	cp ../README.html $(KIT)
	cp ../JRMSUsageGuide.html $(KIT)
#
# jar it all up
#
	rm -rf manifest
	echo "Title: Java(tm) Reliable Multicast(tm) Service" >> manifest
	echo "Version: $(VERSION)" >> manifest
	echo "URL: www.experimentalstuff.com" >> manifest
	(cd ../classes; jar cf $(KIT)/classes.jar *)
	(cd $(KITDIR); jar cmf ../src/manifest jrms$(VERSION).jar *)
	rm -rf manifest

clean:
	rm -rf ../classes
	-rm -rf $(KITDIR)

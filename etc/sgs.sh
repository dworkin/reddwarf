#! /bin/sh
# Bourne shell script for starting the SGS server

# Set this variable to the classpath entries needed to load application
# classes
app_classpath=

# Set this variable to a space-separated list of application
# configuration files
app_config_files=""

# Figure out what platform we're running on and set the os and mach
# variables appropriately.  Here are the supported platforms:
#
# OS		Hardware	BDB subdirectory
# --------	--------	----------------
# Mac OS X	PowerPC		macosx-ppc
# Mac OS X	Intel x86	macosx-x86
# Solaris	Intel x86	solaris-x86
# Solaris	Sparc		solaris-sparc
# Linux		Intel x86	linux-x86
# Windows	Intel x86	win32-x86
#
platform=unknown
os=`uname -s`
case $os in
    Darwin)
	mach=`uname -p`
	case $mach in
	    powerpc)
		platform=macosx-ppc;;
	    i386)
	    	platform=macosx-x86;;
	esac;;
    SunOS)
	mach=`uname -p`
	case $mach in
	    i386)
	    	platform=solaris-x86;;
	    sparc)
	    	platform=solaris-sparc;;
	esac;;
    Linux)
	mach=`uname -m`;
	case $mach in
	    i686)
		platform=linux-x86;;
	esac;;
    CYGWIN*)
	mach=`uname -m`;
	case $mach in
	    i686)
		platform=win32-x86;;
	esac;;
esac

if test $platform = unknown;
then
    echo Unknown operating system: $os;
    exit 1;
fi

set -x

# Run the SGS server, specifying the library path, the logging
# configuration file, the SGS configuration file, the classpath, the
# main class, and the application names.
java -Djava.library.path=lib/bdb/$platform \
     -Djava.util.logging.config.file=sgs.logging \
     -Dcom.sun.sgs.config.file=sgs.config \
     -cp "$app_classpath" \
     -jar lib/sgs.jar \
     $app_config_files

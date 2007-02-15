#! /bin/sh
# Copyright (c) 2007 by Sun Microsystems, Inc.
# All rights reserved.
#
# Bourne shell script for starting the SGS server

# The first argument is the classpath needed to load application
# classes, using the local platform's path separator.  The remaining
# arguments are the names of application configuration files.

# Either set the SGSHOME environment variable to the location of the sgs
# directory, or run from that directory.

if test $# -lt 2; then
    echo Usage: sgs.sh app_classpath app_config_file...;
    exit 1;
fi

# The application classpath
app_classpath="$1"

shift

# The application configuration files
app_config_files="$*"

# The install directory, or the current directory if not set
sgshome="${SGSHOME:=.}"

# Figure out what platform we're running on and set the platform and
# pathsep variables appropriately.  Here are the supported platforms:
#
# OS		Hardware	Platform	Path Separator
# --------	--------	--------------	--------------
# Mac OS X	PowerPC		macosx-ppc	:
# Mac OS X	Intel x86	macosx-x86	:
# Solaris	Intel x86	solaris-x86	:
# Solaris	Sparc		solaris-sparc	:
# Linux		Intel x86	linux-x86	:
# Windows	Intel x86	win32-x86	;
#
platform=unknown
os=`uname -s`
case $os in
    Darwin)
	pathsep=":"
	mach=`uname -p`
	case $mach in
	    powerpc)
		platform=macosx-ppc;;
	    i386)
	    	platform=macosx-x86;;
	    *)
		echo Unknown hardware: $mach;
		exit 1;
	esac;;
    SunOS)
	pathsep=":"
	mach=`uname -p`
	case $mach in
	    i386)
	    	platform=solaris-x86;;
	    sparc)
	    	platform=solaris-sparc;;
	    *)
		echo Unknown hardware: $mach;
		exit 1;
	esac;;
    Linux)
	pathsep=":"
	mach=`uname -m`;
	case $mach in
	    i686)
		platform=linux-x86;;
	    *)
		echo Unknown hardware: $mach;
		exit 1;
	esac;;
    CYGWIN*)
	pathsep=";"
	mach=`uname -m`;
	case $mach in
	    i686)
		platform=win32-x86;;
	    *)
		echo Unknown hardware: $mach;
		exit 1;
	esac;;
    *)
	echo Unknown operating system: $os;
	exit 1;
esac

set -x

# Run the SGS server, specifying the library path, the logging
# configuration file, the SGS configuration file, the classpath, the
# main class, and the application configuration files
java -Djava.library.path=$sgshome/lib/bdb/$platform \
     -Djava.util.logging.config.file=$sgshome/sgs.logging \
     -Dcom.sun.sgs.config.file=$sgshome/sgs.config \
     -cp "$sgshome/lib/sgs.jar$pathsep$app_classpath" \
     com.sun.sgs.impl.kernel.Kernel \
     $app_config_files

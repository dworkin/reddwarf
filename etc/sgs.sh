#! /bin/sh
# Copyright 2007-2008 by Sun Microsystems, Inc. All rights reserved
# Use is subject to license terms.
#
# Bourne shell script for starting the SGS server
#
# The first argument is the classpath needed to load application
# classes, using the local platform's path separator.  The second
# argument is the name of the application configuration file.
#
# Either set the SGSHOME environment variable to the sgs-...
# subdirectory of the installation directory, or run from that
# directory.
#
# Runs java from the value of the JAVA_HOME environment variable, if
# set, or else from the path.

if [ $# -lt 2 ]; then
    echo Usage: sgs.sh app_classpath app_config_file;
    exit 1;
fi

# The application classpath, taken from the first argument
app_classpath="$1"

# The application configuration file, take from the second argument
# following arguments
shift
app_config_file="$1"

# The sgs-... subdirectory of the install directory, or the current
# directory if not set
sgshome="${SGSHOME:=.}"

# The java command
java=java
if [ -n "$JAVA_HOME" ]; then
    java=$JAVA_HOME/bin/java;
fi

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
# Linux		Intel x86_64	linux-x86_64	:
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
	    x86_64)
		platform=linux-x86_64;;
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

# The directory containing the Berkeley DB native libraries
native_dir="$sgshome/lib/bdb/$platform"

# Check that the Berkeley DB libraries have been installed properly
if [ ! -e "$sgshome/lib/bdb/db.jar" ]; then
    echo The db.jar file needs to be installed in $sgshome/lib/bdb
    exit 1;
elif [ ! -e "$native_dir" ]; then
    echo The Berkeley DB native library directory was not found: $native_dir
    exit 1;
fi

set -x

# Run the SGS server, specifying the library path, the logging
# configuration file, the classpath, the main class, and
# the application configuration file
$java -Djava.library.path=$native_dir \
      -Djava.util.logging.config.file=$sgshome/sgs-logging.properties \
      -cp "$sgshome/lib/sgs.jar$pathsep$app_classpath" \
      com.sun.sgs.impl.kernel.Kernel \
      $app_config_file

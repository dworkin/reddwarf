#! /bin/sh
# Copyright 2007 by Sun Microsystems, Inc. All rights reserved
# Use is subject to license terms.
#
# Bourne shell script for starting the SGS server
#
# The first argument is the classpath needed to load application
# classes, using the local platform's path separator.  The remaining
# arguments are the names of application configuration files.
#
# Either set the SGSHOME environment variable to the sgs-...
# subdirectory of the installation directory, or run from that
# directory.
#
# Runs java from the value of the JAVA_HOME environment variable, if
# set, or else from the path.

if [ $# -lt 2 ]; then
    echo Usage: sgs.sh app_classpath app_config_file...;
    exit 1;
fi

# The application classpath, taken from the first argument
app_classpath="$1"

# The application configuration files, take from the second and
# following arguments
shift
app_config_files="$*"

# The sgs-... subdirectory of the install directory, or the current
# directory if not set
sgshome="${SGSHOME:=.}"

# The java command
java=java
if [ -n "$JAVA_HOME" ]; then
    java=$JAVA_HOME/bin/java;
fi

# The path separator for the current platform
os=`uname -s`
case $os in
    CYGWIN*)
	pathsep=";";;
    *)
	pathsep=":";;
esac

set -x

# Run the SGS server, specifying the logging configuration file, the SGS
# configuration file, the classpath, the main class, and the application
# configuration files
$java -Djava.util.logging.config.file=$sgshome/sgs-logging.properties \
      -Dcom.sun.sgs.config.file=$sgshome/sgs-config.properties \
      -cp "$sgshome/lib/sgs.jar$pathsep$app_classpath" \
      com.sun.sgs.impl.kernel.Kernel \
      $app_config_files

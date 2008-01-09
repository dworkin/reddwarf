@rem Copyright 2008 by Sun Microsystems, Inc. All rights reserved
@rem Use is subject to license terms.

@rem Windows batch file for starting the SGS server

@rem The first argument is the classpath needed to load application
@rem classes, using semicolons as the path separator.  The remaining
@rem arguments are the names of application configuration files.

@rem Either set the sgshome environment variable to the location of the
@rem sgs directory, or run from that directory.

@rem Runs java from the value of the JAVA_HOME environment variable, if
@rem set, or else from the path.

@if %2"" == "" goto :usage

@rem The application classpath, taken from the first argument
@set app_classpath=%1

@rem The application configuration files, taken from the second and
@rem following arguments
@shift
@set app_config_files=
:loop
@set app_config_files=%app_config_files% %1
@shift
@if not %1"" == "" goto :loop

@rem The install directory, or the current directory if not set
@if "%sgshome%" == "" (
@set sgshome=.
)

@rem The java command
@set java=java
@if not "%java_home%" == "" (
@set java=%java_home%\bin\java
)

@rem Run the SGS server, specifying the library path, the logging
@rem configuration file, the SGS configuration file, the classpath, the
@rem main class, and the application configuration files
:cmdline
"%java%" -Djava.library.path="%sgshome%\lib\bdb\win32-x86" ^
       	 -Djava.util.logging.config.file="%sgshome%\sgs-logging.properties" ^
       	 -Dcom.sun.sgs.config.file="%sgshome%\sgs-config.properties" ^
       	 -cp "%sgshome%\lib\sgs.jar";%app_classpath% ^
       	 com.sun.sgs.impl.kernel.Kernel ^
       	 %app_config_files%
@goto end

:usage
@echo Usage: sgs app_classpath app_config_file...

:end

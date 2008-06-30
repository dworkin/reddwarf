@rem Copyright 2007-2008 by Sun Microsystems, Inc. All rights reserved
@rem Use is subject to license terms.

@rem Windows batch file for starting the SGS server

@rem The first argument is the classpath needed to load application
@rem classes, using semicolons as the path separator.  The remaining
@rem argument is the names of application configuration file.

@rem Either set the sgshome environment variable to the location of the
@rem sgs-... subdirectory of the installation directory, or run from
@rem that directory.

@rem Runs java from the value of the JAVA_HOME environment variable, if
@rem set, or else from the path.

@if %2"" == "" goto :usage

@rem The application classpath, taken from the first argument
@set app_classpath=%1

@rem The application configuration file, taken from the second argument
@shift
@set app_config_files=%1

@rem The sgs-... subdirectory of the install directory, or the current
@rem directory if not set
@if "%sgshome%" == "" (
@set sgshome=.
)

@rem The java command
@set java=java
@if not "%java_home%" == "" (
@set java=%java_home%\bin\java
)

@rem The directory containing the Berkeley DB native libraries
@set native_dir="%sgshome%\lib\bdb\win32-x86"

@rem Check that the Berkeley DB libraries have been installed properly
@if not exist "%sgshome%\lib\bdb\db.jar" (
@echo The db.jar file needs to be installed in %sgshome%\lib\bdb
@goto end
)
@if not exist "%native_dir%" (
@echo The Berkeley DB native library directory was not found: %native_dir%
@goto end
)

@rem Run the SGS server, specifying the library path, the logging
@rem configuration file, the classpath, the main class, and
@rem the application configuration file
:cmdline
"%java%" -Djava.library.path="%native_dir%" ^
       	 -Djava.util.logging.config.file="%sgshome%\sgs-logging.properties" ^
       	 -cp "%sgshome%\lib\sgs-server.jar";%app_classpath% ^
       	 com.sun.sgs.impl.kernel.Kernel ^
       	 %app_config_files%
@goto end

:usage
@echo Usage: sgs app_classpath app_config_file

:end

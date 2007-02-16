@rem Copyright (c) 2007 by Sun Microsystems, Inc.
@rem All rights reserved.

@rem Windows batch file for starting the SGS server

@rem The first argument is the classpath needed to load application
@rem classes, using semicolons as the path separator.  The remaining
@rem arguments are the names of application configuration files.

@rem Either set the sgshome environment variable to the location of the
@rem sgs directory, or run from that directory.

@if %2"" == "" goto :usage

@rem The application classpath, taken from the first argument unless set
@rem explicitly
@set app_classpath=

@rem The application configuration files, taken from the second and
@rem following arguments unless set explicitly
@set app_config_files=

@rem Set sgshome if it isn't set
@if %sgshome%"" == "" (
@set sgshome=.
)

@rem Set app_classpath to the first argument if it is not set
@if %app_classpath%"" == "" (
@set app_classpath=%1
@shift
)

@rem Set app_config_files to the remaining arguments if it is not set
@if not "" == "%app_config_files%" goto cmdline
:loop
@set app_config_files=%app_config_files% %1
@shift
@if not %1"" == "" goto :loop

@rem Run the SGS server, specifying the library path, the logging
@rem configuration file, the SGS configuration file, the classpath, the
@rem main class, and the application configuration files
:cmdline
java -Djava.library.path=%sgshome%\lib\bdb\win32-x86 ^
     -Djava.util.logging.config.file=%sgshome%\sgs.logging ^
     -Dcom.sun.sgs.config.file=%sgshome%\sgs.config ^
     -cp %sgshome%\lib\sgs.jar;%app_classpath% ^
     com.sun.sgs.impl.kernel.Kernel ^
     %app_config_files%
@goto end

:usage
@echo Usage: sgs app_classpath app_config_file...

:end

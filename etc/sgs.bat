@rem Windows batch file for starting the SGS server

@rem The first argument is the classpath needed to load application
@rem classes, using semicolons as the path separator.  The remaining
@rem arguments are the names of application configuration files.

@rem This script needs to be run from the sgs directory.

@rem The application classpath
@set app_classpath=%0

@rem The application configuration files
@set app_config_files=%1 %2 %3 %4 %5 %6 %7 %8 %9

@rem Run the SGS server, specifying the library path, the logging
@rem configuration file, the SGS configuration file, the classpath, the
@rem main class, and the application configuration files

java -Djava.library.path=lib\bdb\win32-x86 -Djava.util.logging.config.file=sgs.logging -Dcom.sun.sgs.config.file=sgs.config -cp "lib\sgs.jar;%app_classpath%" com.sun.sgs.impl.kernel.Kernel %app_config_files%

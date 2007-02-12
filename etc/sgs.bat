@rem Windows batch file for starting the SGS server -- run this script
@rem from the sgs directory

@rem Set this variable to the semicolon-separated classpath entries
@rem needed to load application classes

@set app_classpath=

@rem Set this variable to a space-separated list of application
@rem configuration files

@set app_config_files=

@rem Run the SGS server, specifying the library path, the logging
@rem configuration file, the SGS configuration file, the classpath, the
@rem main class, and the application configuration files

java -Djava.library.path=lib\bdb\win32-x86 -Djava.util.logging.config.file=sgs.logging -Dcom.sun.sgs.config.file=sgs.config -cp "lib\sgs.jar;%app_classpath%" com.sun.sgs.impl.kernel.Kernel %app_config_files%

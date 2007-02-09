@rem Windows batch file for starting the SGS server

@rem Set this variable to the classpath entries needed to load
@rem application classes

@set app_classpath=

@rem Set this variable to a space-separated list of application names

@set app_names=

@rem Run the SGS server, specifying the library path, the logging
@rem configuration file, the SGS configuration file, the classpath, the
@rem main class, and the application names.

java -Djava.library.path="%cd%"\lib\bdb\win32-x86 -Djava.util.logging.config.file=sgs.logging -Dcom.sun.sgs.config.file=sgs.config -cp "lib\sgs.jar;%app_classpath%" com.sun.sgs.impl.kernel.Kernel %app_names%

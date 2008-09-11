Hack - A Project Darkstar Game

I. INTRODUCTION

The Hack project is an multiplayer, 2D, rogue-like RPG built using the
Project Darkstar MMO application server stack. The project also serves
as a well-documented, example for how to build MMO-like games using
Project Darkstar technology. The Hack code is released into the
Creative Commons Public Domain and is available for unrestricted use.


II. REQUIREMENTS

Hack requires Java 6 to run for the server and Java 5 for the client.
Additionally, Hack is designed to use Maven for its project lifecycle.
If not already installed on your system, Maven may be obtained at:

  http://maven.apache.org/

All other software requirements will be automatically downloaded by
maven upon build.


III. GETTING AND BUILDING THE SOURCE

The source code for Hack may be obtained by svn:

  svn checkout https://darkstar-hack.dev.java.net/svn/darkstar-hack/trunk darkstar-hack --username username

where username is your java.net username. You can also check it out
anonymously using the username guest.

Once code has been obtained, change directories to where you
downloaded hack and run the following from the command line:

  mvn install

This will build all the sources for the project as well as the
javadoc.  The directory structure shoud look as follows:

hack-server

- the code for the hack server

hack-client

- the code for the client and graphical interface

hack-ai

- the code for AI clients

hack-shared

- shared code that is used by the client, server and AI classes

hack-javadoc

- a directory where the javadoc for all the code is built

www

- the java.net website for Hack.


IV. RUNNING

(1) SERVER

Hack is a client-server game and must be run in two parts.  Within the
hack-server directory use the provided script:

  ./run-single-node-server.sh

to run the server in its default setting.  More advanced options exist
within the pom.xml file and can be viewed there.  If you need to start
the Hack server on a different port, configure Hack's Darkstar
application properties file:
 
  hack-server/src/properties/HackSingle.properties

(2) CLIENT

Once the server is running the client can be connected to it.  Within
the hack-client direction use the provided script to connect to the
localhost on the default port:

  ./run-client

If the Hack server is running on a different machine, use the
following command line

  mvn process-test-resources -Ptest-run -Dhack.host=<hostname>

This should launch the client process.  No password is needed to log
in for the current implementation of the server.

(3) AI CLIENTS

Additional AI clients may be run to test server performance and to
simulate client interactions.  from the hack-ai directory, use the
following command line to launch them.

  mvn process-test-resources -Ptest-run -Dai.clients=<number>

Like running the human client, if the Hack server is running on a
different machine, the -Dhack.host option may be specified.


V. COMMUNITY 

If you have questions about Hack or want to participate in the
community, email the development mailing list:

  dev@darkstar-hack.dev.java.net

For more information on Project Darkstar, see their website:

  http://www.projectdarkstar.com/


VI. FURTHER INFORMATION

A current listing of information and frequently asked questsion may be
viewed at 

  https://darkstar-hack.dev.java.net/

A local copy of this site is provided in this repository in the www
directory.  Please see the current site or perform an svn update for
the latest information

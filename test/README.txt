This directory contains a number of test programs for the Project Darkstar
C client implementation. The most useful of these is the test resulting from
compilation of the smokeTestClient.c file (along with testCallbacks.c), which
will result in a program that will test the basic protocol messages between
the C client and a server specially constructed for this test.

The server is the smoketest server, located in the repository

    https://sgs-test.dev.java.net/svn/sgs-tests/trunk/sgs-smoke-test

where there are also instructions on running the server. Once the server is
started, running the smoke test will test the protocol messages, printing out
(on stdout) which (if any) tests have failed, and printing out (on stdout) the
number of tests that have failed. The number of test failures is also returned
as the exit value of the smokeTestClient program.

#! /usr/dist/svr4/bin/perl

# 
# Copyright (c) 2001, Sun Microsystems Laboratories 
# All rights reserved. 
# 
# Redistribution and use in source and binary forms, 
# with or without modification, are permitted provided 
# that the following conditions are met: 
# 
#     Redistributions of source code must retain the 
#     above copyright notice, this list of conditions 
#     and the following disclaimer. 
#             
#     Redistributions in binary form must reproduce 
#     the above copyright notice, this list of conditions 
#     and the following disclaimer in the documentation 
#     and/or other materials provided with the distribution. 
#             
#     Neither the name of Sun Microsystems, Inc. nor 
#     the names of its contributors may be used to endorse 
#     or promote products derived from this software without 
#     specific prior written permission. 
# 
# THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
# CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
# INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
# MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
# DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
# LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
# OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
# PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
# DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
# ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
# OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
# OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
# THE POSSIBILITY OF SUCH DAMAGE. 
#

$mctest="/home/$ENV{USER}/jrms/version1.0/src/reliable/sample_code/mctest";
$outputdir="/home/$ENV{USER}/test";

@receivers = ("bcn","proteus", "bridge" );

$args = join (" " , @ARGV);

if (!($args =~ /^$/) && !($args =~ /^-mr|^-ms|^-hostlist/)) {
	print "Syntax: [-ms <options>] [-mr <options>] [-hostlist <host 1> <host 2> ...]\n"; 
	exit(0);
}

foreach $arg (@ARGV) {
	if ($arg =~ /-mr|-ms|-hostlist/) {
		if ($arg =~ /-mr/) {
			$substitute = "-mr";
		}
		if ($arg =~ /-ms/) {
			$substitute = "-ms";
		}
		if ($arg =~ /-hostlist/) {
			$substitute = "-hostlist";
		}
	}
	else { 
		if ($substitute =~ /-mr/) {
			$mr = $mr . $arg . " ";
		}
		elsif ($substitute =~ /-ms/) {
			$ms = $ms . $arg . " ";
		}
		elsif ($substitute =~ /-hostlist/) {
			$hostlist = $hostlist . $arg . " ";
		}
	} 
		
}

chop ($mr);
chop ($ms);
chop ($hostlist);
print "mr opts: $mr\n";
print "ms opts: $ms\n";
print "hostlist opts: $hostlist\n";

if ($hostlist =~ /^$/) {
	print "Here are receivers: @receivers\n";
}
else {
	@receivers = split(" ", $hostlist);
	print "Here are receivers: @receivers\n";
}

foreach $receiver (@receivers) {
	`chmod 755 $mctest/mr`;
	system("rsh $receiver $mctest/mr $mropts&");
	print "Started $receiver. \n";
}

sleep(10);

$server = `uname -n`;
chop($server);

print "Starting $server server.\n";
`chmod 755 $mctest/ms`;
`$mctest/ms $msopts`;
print "$server server finished.\n";

foreach $receiver (@receivers) {
	if (system("diff $mctest/mctest.in  $outputdir/$receiver/mctestReceive.txt")) {
		print "$outputdir/$receiver/mctestReceive.txt failed.\n";
	} else {
		print "bcn $outputdir/$receiver/mctestReceive.txt passed.\n";
	}
}

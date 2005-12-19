#!/bin/sh -- # perl, to stop looping

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

eval 'exec /usr/dist/svr4/bin/perl -S $0 ${1+"$@"}';

$testtools = "/home/$ENV{USER}/jrms/version1.0/src/reliable/sample_code/testtools";
print ("Calling receiver.sh on local machine.\n");
system("receiver.sh");
system("chmod a+x *.pl");
system("chmod a+x *.sh");
system("chmod a+w *.properties");
system("chmod a+w hostnames.txt");
print "Copying hostnames.txt to your home directory\n";
system("cp hostnames.txt $ENV{HOME}");

open(HOSTS, "hostnames.txt") || die "Could not open hostnames.txt\n";
@hosts = <HOSTS>;
close HOSTS;
# get rid of any entries with pound (#) signs
for (@hosts) {s/^#.*//g};
# get rid of line feeds
for (@hosts) {s/\n//g};
for (@hosts) {
    if ($_ =~ /^$/) { next; }
    print "Starting receiver.sh on $_\n";
    system("rsh $_ \"cd $testtools;$testtools/receiver.sh\"&");
}

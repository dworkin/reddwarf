#!/bin/sh

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

#
# Script to launch the Data Sender test application

if [ "$data_sender_multicast_address" = "" ]; then
    echo "You must set the environment variable data_sender_multicast_address"
    echo "For example:  setenv data_sender_multicast_address 224.148.74.54"
    exit 1
else
    echo "Sending to multicast address $data_sender_multicast_address"
fi

#
# Set up class path and other environment variables
#
. Environment.sh $*

echo "Using files in $jrms_target_tree"

finish_and_exit() {
    trap be_patient 2
    trap be_patient 9
    trap be_patient 15

    if [ -w $data_sender_arguments ]; then
        mv $data_sender_arguments $data_sender_arguments.save
    fi

    savedir=`pwd`
    cd $current_data_sender_log >/dev/null 2>&1
    if [ "$?" = "0" ]; then
        echo "Log files can be found in `pwd`"
    fi
    cd $savedir

    new_dataxfer_log_dir
    exit $status
}

be_patient() {
    echo "Please be patient!.  I'm about to exit."
}

trap finish_and_exit 2
trap finish_and_exit 9
trap finish_and_exit 15

new_dataxfer_log_dir() {
    ###if [ -w $data_receiver_email_file ]; then
        ###cp -f $data_receiver_email_file $current_data_sender_log
	###cat </dev/null > $data_receiver_email_file
    ###fi

    touch $data_receiver_email_file
    chmod 666 $data_receiver_email_file

    if [ ! -h $data_sender_log ]; then
	ln -s /net/bcn.east/files6/projects/jrms/version1.0/src/perf/`basename $data_sender_log` $data_sender_log
    fi

    new_logdir=$data_sender_log/`date +%m-%d-%y.%H:%M:%S`
    echo "making new data transfer log directory $new_logdir" 
    mkdir -p $new_logdir/SAVE

    chmod -R 777 $new_logdir
    rm -f $current_data_sender_log
    ln -s $new_logdir $current_data_sender_log
}

if [ ! -h $current_data_sender_log -o ! -r $current_data_sender_log ]; then
    new_dataxfer_log_dir
fi

log=$current_data_sender_log/DataSender.`uname -n`.`date +%H:%M:%S`

common_arguments="Fttl 20 -Xa $data_sender_multicast_address -Xw 32 -Xs 25000000 -Xd 10 -XA $data_sender_arguments"

arguments="$common_arguments -Xl $log -Xm 1040 $*"

echo $arguments > $data_sender_arguments

echo "Starting DataSender using JDK in $jdk" >> $log
echo "CLASSPATH is $CLASSPATH" >> $log

#$jdk/bin/java -verbosegc -Xgenconfig:1m,1m,SemiSpaces:23M,23M,Blah com.sun.multicast.reliable.applications.stock.DataSender $arguments >> $log

###/net/garbage/files2/detlefs/exactjava/build/solaris/bin/java -verbosegc \
### -Xgenconfig:1m,1m,SemiSpaces:23M,23M,IncMarkSweep com.sun.multicast.reliable.applications.stock.DataSender $arguments >> $log


$jdk/bin/java com.sun.multicast.reliable.applications.stock.DataSender $arguments >> $log 2>&1
status=$?
finish_and_exit

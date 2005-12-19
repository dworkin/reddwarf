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
# This script starts the data receiver, waits
# for it to finish and then restarts it again.
#
# This script is normally started by StockViewer.sh but
# may be invoked manually.
#
if [ "$jrms_classes" = "" ]; then
    verbose="true"
else
    verbose="false"
fi

if [ "$verbose" = "true" ]; then
    echo "Using files in $jrms_target_tree"
fi

#
# Common environment variables such as classpath and jdk path and
# Data Sender / Receiver specific environment variables.
#
if [ "$jrms_source_tree" != "" ]; then
    . $jrms_source_tree/src/perf/Environment.sh $*
else
    . ./Environment.sh $*
fi

finish() {
    email_log=""

    if [ "$receiver_logfile" != "" ]; then
        if [ -w $receiver_logpath/$receiver_logfile ]; then
            echo "`date`:  Receiver exiting..." >> $receiver_logpath/$receiver_logfile
            mv $receiver_logpath/$receiver_logfile $receiver_logpath/SAVE

	    if [ "$verbose" = "true" ]; then
		echo "Log file is in $receiver_logpath/SAVE/$receiver_logfile"
	    else
		#
		# Email the whole log file
		#
                email_log="$receiver_logpath/SAVE/$receiver_logfile"
            fi
        fi
    fi

    #
    # Email that the receiver has finished
    #
    if [ "$verbose" = "false" ]; then
        email_message="DataReceiver exited with status $status, log file is $receiver_logpath/$receiver_logfile"
        ###data_receiver_email 
    fi
}

finish_and_exit() {
    trap be_patient 2
    trap be_patient 9
    trap be_patient 15

    finish
    exit $status
}

be_patient() {
    if [ "$verbose" = "true" ]; then
        echo "Please be patient.  I'm in the process of exiting..."
    fi
}

trap finish_and_exit 2
trap finish_and_exit 9
trap finish_and_exit 15

check_exit() {
    if [ -f /tmp/KillDataReceiver ]; then
	if [ "$verbose" = "true" ]; then
	    echo "Data Receiver stopped."
	fi

	if [ "$receiver_logpath" != "" ]; then
            echo "Data Receiver stopped." >> $receiver_logpath/$receiver_logfile
	fi

        exit 2
    fi
}

rm -f /tmp/KillDataReceiver

while true; do

    receiver_logfile=`uname -n`.`date +%H:%M:%S`
    export receiver_logfile

    check_exit

    if [ ! -h $current_data_sender_log -o ! -r $data_sender_arguments ]; then

	if [ "$verbose" = "true" ]; then
            echo "Waiting for DataSender to Restart..."
	fi

	if [ "$receiver_logpath" != "" ]; then
            echo "Waiting for DataSender to Restart..." >>$receiver_logpath/$receiver_logfile
	fi

        while [ ! -h $current_data_sender_log -o ! -r $data_sender_arguments ]; do
	    check_exit
            sleep 5
        done

    fi

    #
    # Get the absolute path because the link changes when the sender exits
    #
    ###sender_path="`ls -l $current_data_sender_log`"
    ###receiver_logpath=$data_receiver_log/`basename "$sender_path"`
    ###mkdir -p $receiver_logpath/SAVE >/dev/null 2>&1
    ###rm -f $current_data_receiver_log
    ###ln -s $receiver_logpath $current_data_receiver_log
    receiver_logpath=$current_data_receiver_log
    export receiver_logpath

    /bin/ps -ef | grep -v grep | grep 'DataReceiverMonitor.sh' | awk '{print $2}' >/tmp/DataReceiver.pids.$USER 2>&1

    if [ -s /tmp/DataReceiver.pids.$USER ]; then
        kill -9 `cat /tmp/DataReceiver.pids.$USER` >/dev/null 2>&1
    fi

    /bin/ps -ef | grep -v grep | grep 'DataReceiverMonitor.sh' >/dev/null 2>&1
    status=$?
    if [ "$status" -ne "0" ]; then
        sh $jrms_target_tree/src/perf/DataReceiverMonitor.sh $* >> $receiver_logpath/$receiver_logfile &    
    fi

    email_message="Starting DataReceiver, log file is $receiver_logpath/$receiver_logfile"
    email_log=""
    ###data_receiver_email

    command="com.sun.multicast.reliable.applications.stock.DataReceiver \
	`cat $data_sender_arguments` -Rm 16 -Rl $receiver_logpath/$receiver_logfile $*"

    if [ "$verbose" = "true" ]; then
        echo "Starting DataReceiver using JDK in $jdk"
        echo "CLASSPATH is $CLASSPATH"
    fi

    echo "Starting DataReceiver using JDK in $jdk" >> $receiver_logpath/$receiver_logfile
    echo "CLASSPATH is $CLASSPATH" >> $receiver_logpath/$receiver_logfile

    chmod 777 $receiver_logpath/$receiver_logfile >/dev/null 2>&1

    $jdk/bin/java -verbosegc $command >> $receiver_logpath/$receiver_logfile 2>&1
    ###/net/trash/files/detlefs/ej-joe/build/solaris/bin/java -verbosegc $command >> $receiver_logpath/$receiver_logfile
    status=$?

    if [ -f /tmp/KillDataReceiver ]; then
        echo "Data Receiver stopped." >> $receiver_logpath/$receiver_logfile
	finish_and_exit
    fi

    if [ "$status" = "0" ]; then
        if [ "$verbose" = "true" ]; then
            echo "Data Receiver completed normally."
	fi

        echo "Data Receiver completed normally." >> $receiver_logpath/$receiver_logfile
	finish_and_exit
    fi

    if [ "$status" -ne "2" ]; then
        if [ "$verbose" = "true" ]; then
            echo "Data Receiver failed with status $status"
	fi

        echo "Data Receiver failed with status $status" >> $receiver_logpath/$receiver_logfile
	finish_and_exit
	###sleep `random 30`
    fi

    if [ "$verbose" = "true" ]; then
        echo "Data Receiver program restarting..."
    fi

    echo "Data Receiver program restarting..." >> $receiver_logpath/$receiver_logfile

    finish

    sleep `random 5`

done

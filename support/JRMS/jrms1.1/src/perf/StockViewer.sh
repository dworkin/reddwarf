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

setup() {
   if [ "$jrms_target_tree" = "" ]; then
       #
       # Environment variables for the stockviewer and stocksender
       #
       jrms_target_tree=/tmp/jrms.$USER
       export jrms_target_tree

       jrms_source_tree=/net/bcn.east/files2/projects/jrms/version1.0
       export jrms_source_tree

       . $jrms_source_tree/src/perf/Environment.sh $*

       if [ ! -d $jrms_target_tree/src/perf ]; then
           mkdir -p $jrms_target_tree/src/perf
       fi

       if [ ! -d $jrms_target_tree/Kit ]; then
           mkdir -p $jrms_target_tree/Kit
       fi

       copy_files

       $jrms_target_tree/src/perf/StockViewer.sh &
       exit 0
    else
       . $jrms_target_tree/src/perf/Environment.sh $*
    fi
}

finish() {
    email_log=""

    if [ "$viewer_logfile" != "" ]; then
        if [ -w $viewer_logpath/$viewer_logfile ]; then
            mv $viewer_logpath/$viewer_logfile $viewer_logpath/SAVE

	    #
	    # Email the log file and remove it
	    #
            email_log="$viewer_logpath/SAVE/$viewer_logfile"
        fi
    fi

    if [ "$verbose" = "false" ]; then
        email_message="StockViewer exited with status $status, log file is $viewer_logpath/$viewer_logfile"
        stock_viewer_email
    fi

    touch /tmp/KillDataReceiver >/dev/null 2>&1
    chmod 777 /tmp/KillDataReceiver >/dev/null 2>&1
    jrms_target_tree=""
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

#
# Only start data receivers on the east coast.  There's a problem
# with network partitioning w.r.t. the west coast.
#
echo "`domainname`" | grep -i east.sun.com > /dev/null 2>&1
status=$?

if [ "$status" = "0" ]; then
    start_data_receiver="true"
else
    start_data_receiver="false"
fi

if [ "$1" = "-x" ]; then
    start_data_receiver="false"
    shift
fi

rm -f /tmp/KillStockViewer
rm -f /tmp/KillDataReceiver

while true; do

    if [ -f /tmp/KillStockViewer ]; then
	finish_and_exit
    fi

    #
    # Copy files and set environment variables
    #
    setup

    if [ "$verbose" = "" ]; then
        if [ "$jrms_target_tree" = "$jrms_source_tree" ]; then
            verbose="true"
	    echo "Using files in $jrms_target_tree"
        else
            verbose="false"
        fi
    fi

    #
    # When the StockSender starts, it creates a file with its arguments.
    # The StockViewer waits until the StockSender starts so that the
    # viewer can use arguments common to both.
    #
    if [ ! -h $current_stock_sender_log -o ! -r $stock_sender_arguments ]; then
        echo "Waiting for StockSender to Restart..."

        while [ ! -h $current_stock_sender_log -o ! -r $stock_sender_arguments ]; do
            if [ -f /tmp/KillStockViewer ]; then
        	finish_and_exit
            fi

            sleep 5
        done
    fi

    #
    # Get the absolute path because the link changes when the sender exits
    #
    sender_path="`ls -l $current_stock_sender_log`"
    viewer_logpath=$stock_receiver_log/`basename "$sender_path"`
    mkdir -p $viewer_logpath/SAVE >/dev/null 2>&1

    chmod 777 $viewer_logpath >/dev/null 2>&1
    chmod 777 $viewer_logpath/SAVE >/dev/null 2>&1

    viewer_logfile=`uname -n`.`date +%H:%M:%S`
    rm -f $current_stock_receiver_log
    ln -s $viewer_logpath $current_stock_receiver_log

    export viewer_logpath
    export viewer_logfile

    email_message="Starting StockViewer, log file is $viewer_logpath/$viewer_logfile"
    email_log=""
    stock_viewer_email

    hash -r

    #
    # Make sure there is only one data receiver on a given machine.
    #
    if [ "$start_data_receiver" = "true" ]; then
        /bin/ps -ef | grep -v grep | grep 'DataReceiver.sh' >/dev/null 2>&1
        status=$?
	
        if [ "$status" = "0" ]; then
            echo "Data Receiver is already running..." >> $viewer_logpath/$viewer_logfile
        else
    	    echo "Starting the Data Receiver..." >> $viewer_logpath/$viewer_logfile
            sh $jrms_target_tree/src/perf/DataReceiver.sh $* &
        fi
    fi

    command="com.sun.multicast.reliable.applications.stock.StockViewer \
	`cat $stock_sender_arguments` -SChannelFile $jrms_source_tree/src/perf/SunTickerChannel \
	-VSAPTimeout 120 -VSUNTicker -Vl $viewer_logpath/$viewer_logfile $*"

    #
    # Enable logging for host "damage"
    #
    if [ "`uname -n`" = "damage" ]; then
	command="$command -Vm 1023"
    fi

    if [ "`uname -n`" = "gulf-breeze" ]; then
	command="$command -Vm 1023"
    fi

    if [ "`uname -n`" = "pinto" ]; then
	command="$command -Vm 1023"
    fi

    if [ "`uname -n`" = "proteus" ]; then
	command="$command -Vm 1023"
    fi

    echo "Starting StockViewer using JDK in $jdk" >> $viewer_logpath/$viewer_logfile
    echo "CLASSPATH is $CLASSPATH" >> $viewer_logpath/$viewer_logfile

    chmod 777 $viewer_logpath/$viewer_logfile >/dev/null 2>&1

    echo "Starting StockViewer `date`" >> $jrms_source_tree/src/perf/USERS/$USER@`uname -n`.`domainname`
    chmod 777 $jrms_source_tree/src/perf/USERS/$USER@`uname -n` >/dev/null 2>&1

    $jdk/bin/java $command >> $viewer_logpath/$viewer_logfile
    status=$?

    echo "Finished StockViewer `date`" >> $jrms_source_tree/src/perf/USERS/$USER@`uname -n`.`domainname`

    if [ "$status" = "0" ]; then
        echo "StockViewer exiting..." >> $viewer_logpath/$viewer_logfile
	finish_and_exit
    fi

    if [ "$status" != "2" ]; then
	echo "StockViewer exited with status $status" >> $viewer_logpath/$viewer_logfile
	finish_and_exit
    fi

    echo "StockViewer restarting..." >> $viewer_logpath/$viewer_logfile

    finish

    if [ "$jrms_target_tree" != "$jrms_source_tree" ]; then
	jrms_target_tree=""
    fi

    sleep `random 5`

done

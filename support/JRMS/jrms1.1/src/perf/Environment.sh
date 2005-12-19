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

##########################################################
#
# Common environment variables for all of the scripts in this directory.
#

#
# "jrms_target_tree" is the target directory from which all the scripts will be run.
# The intent is to copy the necessary files from "jrms_source_tree" to "jrms"
# so that no one server is overloaded when there are a lot of receivers.
#
# If the scripts are to be run directly from "jrms_source_tree", "jrms_target_tree" 
# can be the same as "jrms_source_tree".
#
if [ "$jrms_target_tree" = "" ]; then
    echo "You must set the environment variable jrms_target_tree!"
    echo "For example:  setenv jrms_target_tree /net/bcn.east/files3/home/jrms/version1.0"
    exit 1
fi

if [ "$jrms_source_tree" = "" ]; then
    jrms_source_tree=$jrms_target_tree
fi

export jrms_source_tree

jrms_classes=$jrms_target_tree/classes
export jrms_classes

jrms_kit=$jrms_target_tree/Kit

if [ "$jdk" = "" ]; then
    jdkfcs=/net/labeast.east/files3/tools/jdk1.2fcs
    exactvm=/net/bcn.east/files2/projects/jrms/Solaris_JDK_1.2.1_04
    jdk=$jdkfcs

    #
    # Use ExactVM if we can
    #
    if [ "$1" != "-noExactVM" ]; then
        uname -r | egrep 5.[78] > /dev/null 2>&1
        status=$?

        if [ "$status" = "0" ]; then
            $exactvm/bin/java -version > /dev/null 2>&1
            status=$?
        fi

        if [ "$status" = "0" ]; then
            jdk=$exactvm
        fi
    fi
fi

CLASSPATH=.:$jdk/lib:$jdkfcs/jce12-rc1-dom/lib/jce12-rc1-dom.jar:$jrms_classes:$jrms_kit/classes.jar
export CLASSPATH

#
# Sleep for some period of time plus 0 to 10 seconds more.
#
random() {
    value="$1"

    if [ "$value" = "" ]; then
	value="1"
    fi

    s="`date +%S` % 10 + $value"
    r=`echo $s | bc`
    echo $r
}

#
# Copies files to local machine
#
copy_files() {
       rm -f $jrms_target_tree/src/perf/DataReceiver.sh
       rm -f $jrms_target_tree/src/perf/DataReceiverMonitor.sh
       rm -f $jrms_target_tree/src/perf/StockViewer.sh
       rm -f $jrms_target_tree/src/perf/Environment.sh

       cp -f $jrms_source_tree/src/perf/DataReceiver.sh $jrms_target_tree/src/perf
       cp -f $jrms_source_tree/src/perf/DataReceiverMonitor.sh $jrms_target_tree/src/perf
       cp -f $jrms_source_tree/src/perf/StockViewer.sh $jrms_target_tree/src/perf
       cp -f $jrms_source_tree/src/perf/Environment.sh $jrms_target_tree/src/perf

       chmod -R 777 $jrms_target_tree >/dev/null 2>&1

       #
       # Copy new classes.jar if it's newer than what's in /tmp/jrms/Kit
       #
       if /usr/bin/test $jrms_target_tree/Kit/classes.jar -ot $jrms_source_tree/Kit/classes.jar ; then
	   rm -f $jrms_target_tree/Kit/classes.jar >/dev/null 2>&1
           cp -f $jrms_source_tree/Kit/classes.jar $jrms_target_tree/Kit/classes.jar
           chmod -R 777 $jrms_target_tree/Kit/classes.jar >/dev/null 2>&1
       fi
}

##########################################################
#
# Environment for Stock sender and receivers.
#

#
# This is a link to the current stock sender log directory
#
current_stock_sender_log=$jrms_source_tree/src/perf/CurrentStockLog
export current_stock_sender_log

#
# This is a link to the current stock receiver log directory
#
current_stock_receiver_log=$jrms_target_tree/src/perf/CurrentStockLog
export current_stock_receiver_log

#
# Directory under which stock sender places its logs
#
stock_sender_log=$jrms_source_tree/src/perf/StockLog
export stock_sender_log

#
# Directory under which stock receiver places its logs
#
stock_receiver_log=$jrms_target_tree/src/perf/StockLog
export stock_receiver_log

#
# Sender arguments are stored in files so that the receivers
# can use the arguments common to sender and receiver
#
stock_sender_arguments=$jrms_source_tree/src/perf/StockSender.arguments
export stock_sender_arguments

#
# Channel file is located where the sender is started.  All the viewers
# need to use this if they don't see the SAP advertisement.
#
channelfile_path=$jrms_source_tree/src/perf

#
# Location of the email file for the stockviewers.  This must match
# the alias StockViewerEmail@proteus.East.Sun.COM  !!!
#
stock_viewer_email_file=/net/bcn.east/files2/projects/jrms/version1.0/src/perf/StockViewerEmail

#
# Email to stockviewer email file
#
stock_viewer_email() {
    tmpfile=/tmp/stock_viewer.email.$USER.`date +%H:%M:%S`.`random 1`
    rm -f "$tmpfile"
    echo "" > "$tmpfile"
    echo "" >> "$tmpfile"
    echo "`date`" >> "$tmpfile"
    echo "`uname -n`.`domainname`" >> "$tmpfile" 
    echo "`/usr/bin/id`" >> "$tmpfile"
    echo "$email_message" >> "$tmpfile"
    chmod 777 "$tmpfile" >/dev/null 2>&1

    if [ "$email_log" != "" ]; then
	sleep `random 1`
        /usr/ucb/mail StockViewerEmail@proteus.East.Sun.COM < $email_log >/dev/null 2>&1
	rm -f $email_log
    fi

    sleep `random 1`
    /usr/ucb/mail StockViewerEmail@proteus.East.Sun.COM < "$tmpfile" >/dev/null 2>&1
    rm -f "$tmpfile"
}

##########################################################
#
# Environment for Data Sender and Receivers
#

#
# This is a link to the current data sender data log directory
#
current_data_sender_log=$jrms_source_tree/src/perf/CurrentDataXferLog
export current_data_sender_log

#
# This is a link to the current data receiver log directory
#
current_data_receiver_log=$jrms_source_tree/src/perf/CurrentDataXferLog
export current_data_receiver_log

#
# Directory under which data sender places its logs
#
data_sender_log=$jrms_source_tree/src/perf/DataXferLog
export data_sender_log

#
# Directory under which data receiver places its logs
#
data_receiver_log=$jrms_target_tree/src/perf/DataXferLog
export data_receiver_log

#
# Sender arguments are stored in files so that the receivers
# can use the arguments common to sender and receiver
#
data_sender_arguments=$jrms_source_tree/src/perf/DataSender.arguments
export data_sender_arguments

#
# Location of the email file for the data receivers.  This must match
# the alias DataReceiverEmail@proteus.East.Sun.COM !!!
#
data_receiver_email_file=/net/bcn.east/files2/projects/jrms/version1.0/src/perf/DataReceiverEmail

#
# Email to data receiver email file
#
data_receiver_email() {
    tmpfile=/tmp/data_receiver.email.$USER.`date +%H:%M:%S`.`random 1`
    rm -f "$tmpfile"
    echo "" > "$tmpfile"
    echo "" >> "$tmpfile"
    echo "`date`  " >> "$tmpfile"
    echo "`uname -n`.`domainname`" >> "$tmpfile"
    echo "`/usr/bin/id`" >> "$tmpfile"
    echo "$email_message" >> "$tmpfile"
    chmod 777 "$tmpfile" >/dev/null 2>&1

    if [ "$email_log" != "" ]; then
        sleep `random 1`
        /usr/ucb/mail DataReceiverEmail@proteus.East.Sun.COM < $email_log >/dev/null 2>&1
	rm -f $email_log
    fi

    sleep `random 1`
    /usr/ucb/mail DataReceiverEmail@proteus.East.Sun.COM < "$tmpfile" >/dev/null 2>&1
    rm -f "$tmpfile"
}

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
# Script to launch the example JRMS Stock Sender application.

if [ "$stock_sender_multicast_address" = "" ]; then
    echo "You must set the environment variable stock_sender_multicast_address"
    echo "For example:  setenv stock_sender_multicast_address 224.148.75.55"
    exit 1
else
    echo "Sending to multicast address $stock_sender_multicast_address"
fi

#
# Set up class path and other environment variables
#
. Environment.sh $*

finish_and_exit() {
    trap be_patient 2
    trap be_patient 9
    trap be_patient 15

    if [ -w $stock_sender_arguments ] ; then
        mv $stock_sender_arguments $stock_sender_arguments.save 
    fi

    savedir=`pwd`
    cd $current_stock_sender_log > /dev/null 2>&1
    if [ "$?" = "0" ]; then
        echo "Log files can be found in `pwd`"
    fi
    cd $savedir

    new_stock_log_dir
    exit $status
}

be_patient() {
    echo "Please be patient!.  I'm about to exit."
}

trap finish_and_exit 2
trap finish_and_exit 9
trap finish_and_exit 15

#
# Create new log directories
#
new_stock_log_dir() {
    #
    # The path to the email file must match the alias StockViewerEmail@proteus!
    #
    if [ -w $stock_viewer_email_file ]; then
        cp -f $stock_viewer_email_file $current_stock_sender_log
	cat </dev/null > $stock_viewer_email_file
    fi

    touch $stock_viewer_email_file
    chmod 666 $stock_viewer_email_file

    if [ ! -h $stock_sender_log ]; then
        ln -s /net/bcn.east/files6/projects/jrms/version1.0/src/perf/`basename $stock_sender_log` $stock_sender_log
    fi

    new_logdir=$stock_sender_log/`date +%m-%d-%y.%H:%M:%S`
    echo "making new stock log directory $new_logdir"
    mkdir -p $new_logdir/SAVE
    chmod 777 $new_logdir
    chmod 777 $new_logdir/SAVE
    rm -f $current_stock_sender_log
    ln -s $new_logdir $current_stock_sender_log
}

if [ ! -h $current_stock_sender_log -o ! -r $current_stock_sender_log ]; then
    new_stock_log_dir
fi

log=$current_stock_sender_log/StockSender.`uname -n`.`date +%H:%M:%S`

common_arguments="-Sttl 20 -Sa $stock_sender_multicast_address"
arguments="$common_arguments -SSunTicker -STickers ^DJI+^IXIC+^SPC+A+AAPL+AMAT+AOL+ASPT+AWE+BRCM+BRKb+BTF+CCRD+CMGI+COMS+CPQ+CSCO+DELL+EBAY+EMC+ENMD+EPRS+EXTR+FDRY+GE+HAND+HWP+IBM+IFMX+INTC+JDSU+JNPR+KAB+KELL+LU+LVLT+MINI+MOT+MSFT+MTY+NMPS+NT+ORCL+PALM+PRSW+QCOM+RATL+RHAT+RINO+SLR+SUMX+SUNW+T+TXN+VIIIX+VRTS+VTSS+WCOM+WWF+YHOO -Sl $log -SA $stock_sender_arguments $*"

echo $arguments > $stock_sender_arguments

echo "Starting StockSender using JDK in $jdk" >> $log
echo "CLASSPATH is $CLASSPATH" >> $log
$jdk/bin/java com.sun.multicast.reliable.applications.stock.StockServer $arguments >> $log
status=$?
finish_and_exit

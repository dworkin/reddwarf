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

PWD=`pwd`
DESTPATH=$(PWD)/../../../../classes

SRCS = BeaconPacket.java \
       BeaconPacketEvent.java \
       BeaconPacketListener.java \
       BeaconGenThread.java \
       bricks.java \
       CSTATE.java \
       GroupMgmtBlk.java \
       GroupMgmtThread.java \
       GroupMembershipMask.java \
       HSTATE.java \
       HelloThread.java \
       InputDispThread.java \
       InvalidChecksumException.java \
       LSTATE.java \
       MESGTYPE.java \
       MROLE.java \
       MemberBlock.java \
       OutputDispThread.java \
       PacketDb.java \
       PacketDbIOManager.java \
       TRAMAckPacket.java \
       TRAMAckPacketEvent.java \
       TRAMAckPacketListener.java \
       TRAMAmPacket.java \
       TRAMAmPacketEvent.java \
       TRAMAmPacketListener.java \
       TRAMConfigurator.java \
       TRAMControlBlock.java \
       TRAMControlBlockException.java \
       TRAMDataPacket.java \
       TRAMDataPacketEvent.java \
       TRAMDataPacketListener.java \
       TRAMHeadAck.java \
       TRAMHelloPacket.java \
       TRAMHelloPacketEvent.java \
       TRAMHelloPacketListener.java \
       TRAMHelloUniPacket.java \
       TRAMHelloUniPacketEvent.java \
       TRAMHelloUniPacketListener.java \
       TRAMInputOutput.java \
       TRAMInvalidConfigException.java \
       TRAMLogger.java \
       TRAMLogData.java \
       TRAMMemberAck.java \
       TRAMMembershipEvent.java \
       TRAMMembershipListener.java \
       TRAMPacket.java \
       TRAMRmPacket.java \
       TRAMRmPacketEvent.java \
       TRAMRmPacketListener.java \
       TRAMSeqNumber.java \
       TRAMSimpleTimer.java \
       TRAMSimulator.java \
       TRAMStats.java \
       TRAMTimerEventHandler.java \
       TRAMTimer.java \
       TRAMTransportProfile.java \
       TRAM_INFO.java \
       TRAM_STATE.java \
       MulticastSenderPacket.java \
       MulticastSnifferPacket.java \
       MulticastSenderStream.java \
       MulticastSnifferStream.java \
       SUBMESGTYPE.java \
       TMODE.java \
       UcastInputDispThread.java \
       TRAMInputStream.java \
       TRAMOutputStream.java \
       TRAMPacketSocket.java \
       TRAMStreamSocket.java \
       TRAMTimerTest.java \
       TRAMHaPacket.java \
       TRAMHaPacketEvent.java \
       TRAMHaPacketListener.java \
       TRAMHbPacket.java \
       TRAMHbPacketEvent.java \
       TRAMHbPacketListener.java \
       TRAMMsPacket.java \
       TRAMMsPacketEvent.java \
       HeadBlock.java \
       TRAMMsPacketListener.java \
       TRAMCacheControl.java \
       TRAMGenericDataCache.java \
       TRAMDataCache.java \
       TRAMRateAdjuster.java \
       AbortTRAM.java \
       ChangeTRAMLoggingOption.java \
       TRAMLoggingOptionPacket.java \
       TRAMVector.java \
       BrickLayer.java \
       PerfMon.java

.SUFFIXES: .java .class

.java.class:
	javac $(JAVAFLAGS) -d $(DESTPATH) $<

all: 
	javac $(JAVAFLAGS) -d $(DESTPATH) $(SRCS)

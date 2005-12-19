/* 
 * Copyright (c) 2001, Sun Microsystems Laboratories 
 * All rights reserved. 
 * 
 * Redistribution and use in source and binary forms, 
 * with or without modification, are permitted provided 
 * that the following conditions are met: 
 * 
 *     Redistributions of source code must retain the 
 *     above copyright notice, this list of conditions 
 *     and the following disclaimer. 
 *             
 *     Redistributions in binary form must reproduce 
 *     the above copyright notice, this list of conditions 
 *     and the following disclaimer in the documentation 
 *     and/or other materials provided with the distribution. 
 *             
 *     Neither the name of Sun Microsystems, Inc. nor 
 *     the names of its contributors may be used to endorse 
 *     or promote products derived from this software without 
 *     specific prior written permission. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND 
 * CONTRIBUTORS ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, 
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF 
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE 
 * DISCLAIMED. IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE 
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, 
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, 
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND 
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, 
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY 
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF 
 * THE POSSIBILITY OF SUCH DAMAGE. 
 */

/*
 * GroupMembershipMask.java
 * 
 * Module Description:
 * The class defines the TRAM Group Membership Mask.
 * For every member accepted by the Group management
 * a bit position in the mask space is specifically
 * allocated for that member. A memberId is used as a
 * link to tie a member to a specific bit.
 */
package com.sun.multicast.reliable.transport.tram;

import java.lang.*;

class GroupMembershipMask implements Cloneable {
    TRAMLogger logger = null;
    private int[] memberMask;

    /*
     * Constructors
     */

    public GroupMembershipMask(int maxMemberCount) {
        allocateAndInitialize(maxMemberCount);
    }

    public GroupMembershipMask(int maxMemberCount, TRAMLogger logger) {
        this.logger = logger;

        allocateAndInitialize(maxMemberCount);
    }

    public static void main(String args[]) {
        GroupMembershipMask gMask = new GroupMembershipMask(69);

        for (int i = 0; i < 69; i++) {
            System.out.println("Adding new member bit " 
                               + gMask.assignNewMemberBit());
        }

        System.out.println("\n\nClearing member with id 1");
        gMask.clearMemberBit(1);
        System.out.println("Adding new member bit " 
                           + gMask.assignNewMemberBit());
        System.out.println("\n\nClearing member with id 32");
        gMask.clearMemberBit(32);
        System.out.println("Adding new member bit " 
                           + gMask.assignNewMemberBit());
        System.out.println("\n\nClearing member with id 33");
        gMask.clearMemberBit(33);
        System.out.println("Adding new member bit " 
                           + gMask.assignNewMemberBit());
        System.out.println("\n\nClearing member with id 64");
        gMask.clearMemberBit(64);
        System.out.println("Adding new member bit " 
                           + gMask.assignNewMemberBit());
        System.out.println("\n\nClearing member with id 65");
        gMask.clearMemberBit(65);
        System.out.println("Adding new member bit " 
                           + gMask.assignNewMemberBit());
        gMask.clearMemberBit(5);

        GroupMembershipMask gMaskTemp = null;

        try {
            gMaskTemp = (GroupMembershipMask) gMask.clone();
        } catch (CloneNotSupportedException ce) {
            System.out.println("Clone NOT Supported ");
            System.exit(1);
        }

        System.out.println("Adding new member bit to Clone" 
                           + gMaskTemp.assignNewMemberBit());
        gMaskTemp.clearMemberBit(1);
        System.out.println("Adding new member bit to Orig Mask" 
                           + gMask.assignNewMemberBit());
    }

    /**
     * private method to Allocate and initialize the Membership
     * mask
     */
    private void allocateAndInitialize(int maxMemberCount) {
        int size = (maxMemberCount + 31) / 32;

        memberMask = new int[size];

        if (logger == null) {
            System.out.println("Member Count == " + maxMemberCount 
		+ " Allocated " + memberMask.length);
        } else {
	    if (logger.requiresLogging(TRAMLogger.LOG_VERBOSE)) {
                logger.putPacketln(this, 
		    "Member Count == " + maxMemberCount 
                    + " Allocated " + memberMask.length);
	    }
        }

        for (int i = 0; i < memberMask.length; i++) {
            memberMask[i] = 0;
        }
    }

    /**
     * public method to assign a new Member Id.
     * 
     * @return int returns a member Id that is to be assigned to a
     * newly added member. The member bit slot in the
     * Member Mask will be marked as being used.
     */
    public synchronized int assignNewMemberBit() {
        int i, j;
        boolean idFound = false;

        for (i = 0; i < memberMask.length; i++) {
            int bitPosition = 0;

            if (memberMask[i] != 0xffffffff) {
                for (j = 0; j < 32; j++) {
                    bitPosition = (1 << j);

                    if ((memberMask[i] & bitPosition) == 0) {

                        // yes we found a free slot.

                        int memId = ((i * 32) + (j + 1));

                        // mark the member bit position as being used.

                        memberMask[i] = (memberMask[i] | bitPosition);

                        if (logger != null) {
			    if (logger.requiresLogging(
				TRAMLogger.LOG_VERBOSE)) {

                                logger.putPacketln(this, 
                                    "Assigning MemberId " + memId 
                                    + " Index = " + i + "Mask = " 
                                    + memberMask[i]);
			    }
                        } else {
                            System.out.println("Assigning MemberId " + memId + 
				" Index = " + i + "Mask = " + memberMask[i]);
                        }

                        return memId;
                    }
                }
            }
        }

        return 0;       // Invalid member id.
    }

    /**
     * method to clear bit related to the member in the MASK. The bitmask
     * slot of the freed member Id will be marked as unused.
     * 
     * @param memberId the member Id that is to be cleared.
     * 
     */
    public synchronized void clearMemberBit(int memberId) {
        if ((memberId > (memberMask.length * 32)) || (memberId < 0)) {
            return;
        } 

        memberId = memberId - 1;

        int index = memberId / 32;
        int memberBitMask = 1 << (memberId - (32 * index));

        /*
         * now reset the bit mask so that no more data packets will
         * held for this member.
         */
        memberMask[index] &= (~(memberBitMask));
    }

    /**
     * method to set the member bit in the MASK.
     * 
     * @param memberId the member Id bit that is to be set.
     * 
     */
    public synchronized void setMemberBit(int memberId) {
        if ((memberId > (memberMask.length * 32)) || (memberId < 0)) {
            return;
        } 

        memberId = memberId - 1;

        int index = memberId / 32;
        int memberBitMask = 1 << (memberId - (32 * index));

        /*
         * now reset the bit mask so that no more data packets will
         * held for this member.
         */
        memberMask[index] |= (memberBitMask);
    }

    /**
     * method to clone the MembershipMask
     */
    public Object clone() throws CloneNotSupportedException {
        GroupMembershipMask gMaskTemp = null;

        gMaskTemp = new GroupMembershipMask((memberMask.length * 32), logger);

        gMaskTemp.copyMask(memberMask);

        return gMaskTemp;

    /* return super.clone(); */

    }

    /**
     * A protected method to copy the individual mask elements. This is
     * to be done as the super.clone() method does not copy the individual
     * elements of the mask... just copies the reference to it.
     * 
     */
    protected void copyMask(int mask[]) {
        for (int i = 0; i < memberMask.length; i++) {
            memberMask[i] = mask[i];
        }
    }

    /**
     * method to test if the Member Mask is empty
     */
    public boolean isMaskEmpty() {
        for (int i = 0; i < memberMask.length; i++) {
            if (memberMask[i] != 0) {
                return false;
            } 
        }

        return true;
    }

}


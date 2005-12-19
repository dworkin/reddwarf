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
 * MROLE.java
 */
package com.sun.multicast.reliable.transport.tram;

/**
 * Constants to define the role played by a member(or a node) in
 * TRAM multicast repair tree.
 */
public class MROLE {

    /**
     * The constant MEMBER_ONLY is used to specify member to perform the
     * role of a simple recipient of multicast data. A MEMBER_ONLY node
     * does not perform multicast repairs.
     */
    public static final byte MEMBER_ONLY = 1;

    /**
     * The constant MEMBER_EAGER_HEAD is used to specify member to perform the
     * role of a preferred repair node in the TRAM repair tree. Nodes looking
     * for repair nodes to attach to the TRAM repair tree, chooses repair
     * node with MROLE of MEMBER_EAGER_HEAD over MEMBER_RELUCTANT_HEAD.
     */
    public static final byte MEMBER_EAGER_HEAD = 2;

    /**
     * The constant MEMBER_RELUCTANT_HEAD used to specify member to
     * assume the role of a repair node only if no other repair nodes
     * with MROLE of MEMBER_EAGER_HEAD is accepting new members in the
     * neighborhood. Nodes looking to attach to the TRAM repair tree
     * chooses repair node that have MROLE of MEMBER_EAGER_HEAD over
     * MEMBER_RELUCTANT_HEAD.
     */
    public static final byte MEMBER_RELUCTANT_HEAD = 3;
}


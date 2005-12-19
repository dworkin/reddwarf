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
 * TRAMVector.java
 * 
 * Module Description: Defines the TRAMVector class. This is done 
 *		       to enable use of the removeRange() method of
 *                     the java.util.Vector. Hence for all practical
 *                     purposes, this class is very much like the
 *                     java.util.Vector.
 */
package com.sun.multicast.reliable.transport.tram;
import java.util.*;

/*
 * Class to extend Vector.
 */
public class TRAMVector extends Vector {

    /**
     * Constructor same of theat of Vector.
     */
    public TRAMVector() {
	super();
    }
	
    /**
     * Constructor same of theat of Vector.
     */
    public TRAMVector(Collection c) {
	super(c);
    }

    /**
     * Constructor same of theat of Vector.
     */
    public TRAMVector(int initialCapacity) {
	super(initialCapacity);
    }

    /**
     * Constructor same of theat of Vector.
     */
    public TRAMVector(int initialCapacity, int capacityIncrement) {
	super(initialCapacity, capacityIncrement);
    }

    /**
     * The method java.util.Vector.removeRange() is a protected
     * method, hence no class can invoke it unless it extends
     * the Vector class.... Hence the existance of this class.
     */
    public void rmRange(int fromIndex, int toIndex) {
	removeRange(fromIndex, toIndex);
    }
}

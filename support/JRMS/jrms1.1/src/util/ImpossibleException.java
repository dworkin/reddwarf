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
 * ImpossibleException.java
 */
package com.sun.multicast.util;

/**
 * Signals that an exception that should never occur has occurred.
 * This exception is a RuntimeException so that it may be
 * thrown without requiring it to be declared.
 */
public class ImpossibleException extends RuntimeException {

    /**
     * Creates an <code>ImpossibleException</code> for the
     * given exception.
     * @param e the exception that occurred
     */
    public ImpossibleException(Exception e) {
        super();

        internalException = e;
    }

    /**
     * Produce the message, include the message from the internal
     * exception if there is one.
     */
    public String getMessage() {
        if (internalException == null) {
            return super.getMessage();
        } else {
            return super.getMessage() + "; internal exception is: \n\t" 
                   + internalException.toString();
        }
    }

    /**
     * Print a stack trace, including the internal exception.
     */
    public void printStackTrace() {
        if (internalException == null) {
            super.printStackTrace();
        } else {
            super.printStackTrace();
            System.err.println("; stack trace for internal exception is:");
            internalException.printStackTrace();
        }
    }

    private Exception internalException;
}

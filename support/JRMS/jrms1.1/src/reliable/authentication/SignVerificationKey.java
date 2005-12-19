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
 * SignVerificationKey
 * 
 * Module Description:
 * 
 * This module provides authentication services to any
 * module that requires such a service.
 */
package com.sun.multicast.reliable.authentication;

import java.security.spec.*;
import java.security.PublicKey;
import java.io.*;

class SignVerificationKey extends Object implements java.io.Serializable {
    String alias;
    int id;
    PublicKey key;

    /*
     * Constructors.
     */

    public SignVerificationKey(String alias, PublicKey key) {
        super();

        this.alias = alias;
        this.key = key;
    }

    public SignVerificationKey(int id, PublicKey key) {
        super();

        this.id = id;
        this.key = key;
    }

    public SignVerificationKey(String alias, int id, PublicKey key) {
        super();

        this.alias = alias;
        this.id = id;
        this.key = key;
    }

    /*
     * returns the stored verification key.
     * 
     * @return key - the sign verification key.
     */

    public PublicKey getKey() {
        return key;
    }

    /*
     * method to test if the passed in alias and the key alias match.
     * 
     * @return true if the aliases match.
     * false if the alias does not match.
     */

    public boolean contains(String alias) {
        if (this.alias.compareTo(alias) == 0) {
            return true;
        } 

        return false;
    }

}


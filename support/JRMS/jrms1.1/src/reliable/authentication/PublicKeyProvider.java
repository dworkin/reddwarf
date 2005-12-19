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
 * PublicKeyProvider.java
 */
package com.sun.multicast.reliable.authentication;

import java.io.*;
import java.util.*;
import java.security.*;

/**
 * The SUN Security Provider.
 */

/**
 * Defines the PublicKeyProvider provider.
 * 
 */

/** 
 * An extension of the Provider interface to include a KeyStore for PrivateKeys.
 */
public final class PublicKeyProvider extends Provider {
    private static final String INFO = 
	"PublicKeyProvider " + "(PublicKeyKS keystore)";

    /**
     * Declares the PublicKeyProvider Provider extension
     */
    public PublicKeyProvider() {

        /* We are the PublicKeyProvider provider */

        super("PublicKeyProvider", 1.0, INFO);

        AccessController.doPrivileged(new java.security.PrivilegedAction() {

            public Object run() {

                /*
                 * KeyStore
                 */
                put("KeyStore.PublicKeyKS", 
		    "com.sun.multicast.reliable.provider.PublicKeyStore");

                return null;
            }

        });
    }

}


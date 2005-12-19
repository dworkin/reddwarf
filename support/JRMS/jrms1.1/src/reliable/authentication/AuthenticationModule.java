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
 * AuthenticationModule
 * 
 * Module Description:
 */
package com.sun.multicast.reliable.authentication;

import java.security.*;
import java.security.spec.*;
import java.util.*;
import java.io.*;
import java.net.*;

/**
 * ***************************************************************
 * This module is responsible for providing authentication services
 * to the RM Transport protocol.
 */
public class AuthenticationModule {
    AuthenticationSpec authenticationSpec;
    String password;
    Signature toSign = null;
    Signature toVerify = null;
    int signatureSize = 0;

    /**
     * Constructor.
     */
    public AuthenticationModule(AuthenticationSpec spec, 
                                String password) throws SignatureException {
        authenticationSpec = spec;
        this.password = password;

        // load up the providers just in case we haven't already

        try {
            java.security.Provider jceprov = 
                new com.sun.crypto.provider.SunJCE();

            Security.addProvider(jceprov);

            java.security.Provider jrmsprov = new 
		com.sun.multicast.reliable.authentication.PublicKeyProvider();

            Security.addProvider(jrmsprov);
        } catch (Exception ie) {
            ie.printStackTrace();
            System.exit(1);
        }

        /*
         * First check if the module is expected to sign messages are not.
         * If so, then sign a dummy message to determine the length of the
         * signature the algorithm-key pair generates. This value will be
         * used to allocate enough space in the data message so that the
         * signature can be accomodated. By allocating enough space before
         * hand, we avoid unnecessary allocation and copies of data before
         * it is transmitted.
         */
        if (spec.getSignatureKey(password) != null) {

            /*
             * Yes, it needs to sign data.... now compute the typical
             * signature length.
             */
            try {
                toSign = Signature.getInstance(authenticationSpec.
		    getSignatureAlgorithm());

                toSign.initSign(spec.getSignatureKey(password));

                /* Ha a dummy string!..... */

                String text = 
                    "Dah Dah Dah.... Booh Booh Booh Signature Test!";

                toSign.update(text.getBytes());

                byte[] signedVal = toSign.sign();

                /*
                 * Update the signatureSize field. This will be used to
                 * allocate space while creating a datagram packet to send.
                 */
                signatureSize = signedVal.length;
            } catch (NoSuchAlgorithmException se) {
                throw new SignatureException();
            } catch (InvalidKeyException ie) {
                throw new SignatureException();
            }
        }

        // Now initialize the Verification Keys.

        try {
            toVerify = Signature.getInstance(
		authenticationSpec.getSignatureAlgorithm());
        } catch (Exception e) {
            throw new SignatureException();
        }
    }

    /**
     * Method to add a signature verification key to the module.
     * 
     * @param alias the alias of the verification that is being added.
     * @param verifyKey the verification Key itself!.
     * 
     * @Exception KeyException when a verifykey bearing the same alias is
     * already present in the module. For key
     * replacement, first delete the key entry
     * and then add it.
     * 
     */
    public synchronized void addSignVerificationKey(String alias, 
            PublicKey verifyKey) throws KeyException {
        authenticationSpec.addSignVerificationKey(alias, verifyKey);
    }

    /**
     * Method to remove a signature verification key from the module.
     * 
     * @param alias the alias of the verification that is being removed.
     * 
     */
    public synchronized void deleteSignVerificationKey(String alias) {
        authenticationSpec.deleteSignVerificationKey(alias);
    }

    /**
     * Method to Sign a block of message.
     * 
     * The method requires the key that is to be used to sign the
     * message(typically  the private key) is already initialized.
     * this initializaion takes place at the time of initailization
     * of this class - details are found in the 'Spec'.
     * 
     * @param  data -      data message that is to be signed.
     * 
     * @return signature - signature for the block of data.
     * 
     * @Exception SignatureException is thrown if the signature module
     * module is not proerly initialized.
     */
    public synchronized final byte[] sign(byte[] data) 
            throws SignatureException {
        toSign.update(data);

        return toSign.sign();
    }

    /**
     * Method to Sign a block of message.
     * 
     * The method requires the key that is to be used to sign the
     * message(typically  the private key) is already initialized.
     * this initializaion takes place at the time of initailization
     * of this class - details are found in the 'Spec'.
     * 
     * @param  data -      data message that is to be signed.
     * 
     * @return signature - signature for the block of data.
     * 
     * @Exception SignatureException is thrown if the signature module
     * module is not proerly initialized.
     */
    public synchronized final byte[] sign(byte[] data, int offset, int length) 
            throws SignatureException {
        toSign.update(data, offset, length);

        return toSign.sign();
    }

    /**
     * Method to verify Signature.
     * 
     * @param  data -      data message that is to be authenticated.
     * @param  signature - Message signature
     * @param  alias -     an sender's user id or an alias that can
     * used to access the relavent public key.
     * 
     * @return true        if signature verifies.
     * false       if the signature fails to verify.
     * 
     * @Exception SignatureException is thrown if the signature module
     * module is not properly initialized.
     */
    public synchronized final boolean verify(byte[] data, int offset, 
                                             int length, byte[] signature, 
                                             String alias) throws KeyException, 
                                             SignatureException {
        PublicKey pkey;

        try {
            pkey = authenticationSpec.getSignVerificationKey(alias);
        } catch (KeyException ke) {
            throw new KeyException();
        }

        toVerify.initVerify(pkey);
        toVerify.update(data, offset, length);

        return toVerify.verify(signature);
    }

    /**
     * returns the typical length of the signature produced by the module
     * for a given algorithm-key pair.
     * 
     * @return the maximum length of the signature produced by the module.
     */
    public int getSignatureSize() {
        return signatureSize + 10;

        /*
         * Why fudge factor ? the length of the
         * signature varies from (x) to (x + y).
         * In the examples seen so far, the y
         * value varies between 1 to 4 bytes.
         * That is if you sign a block of data,
         * the sig length may come out as 47 bytes.
         * if you repeat the same excercise again
         * you might get a value of either 46 or
         * 48. It appears to toggle between these
         * values and it appears to be random.
         * Since I really don'y know the logistics
         * behind it, I have simple added a fudge
         * factor to it (:-). This will have to be
         * revisted to choose a logical and better
         * suited value. FIXME.
         */
    }

}


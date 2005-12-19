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
 * AuthenticationSpec
 * 
 * Module Description:
 */
package com.sun.multicast.reliable.authentication;

import java.security.*;
import javax.crypto.*;
import java.security.spec.*;
import java.util.*;
import java.io.*;
import java.net.*;
import com.sun.multicast.util.Util;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import sun.security.x509.AlgorithmId;
import sun.security.x509.X509CertImpl;
import sun.security.x509.X509CertInfo;
import sun.security.x509.X500Name;
import sun.security.x509.CertAndKeyGen;
import sun.security.x509.CertificateSubjectName;
import sun.security.x509.CertificateIssuerName;
import sun.security.x509.CertificateValidity;
import sun.security.x509.CertificateSerialNumber;
import sun.security.x509.CertificateAlgorithmId;
import sun.security.x509.X509Key;
import sun.security.x509.X500Signer;

public class AuthenticationSpec implements java.io.Serializable {

    /**
     * This is a specification that will be used to initialize the
     * Authentication Module.
     */
    public static final String DEFAULT_SPEC_FILENAME = "JRMSAuth.spec";
    Vector signVerificationKeys = new Vector();
    String signatureAlgorithm;      // Signature algorithm to be used.
    String signatureProvider;
    private String keyStoreProvider = null;
    private String keyStoreFileName = null;

    public static void main(String[] args) {
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

        Provider p[] = Security.getProviders();
        AuthenticationSpec spec = new AuthenticationSpec();
        PublicKey keyPublic;

        if (args.length < 7) {
            System.out.println("usage: AuthenticationSpec Algorithm " +
		"SenderNode keyStoreProvider keyStoreFileName password " +
		" SenderSpecName ReceiverSpecName");
            System.exit(1);
        }

        String algorithm = args[0];
        String sNode = args[1];
        String keyStoreProvider = args[2];
        String keyStoreFileName = args[3];
        String password = args[4];
        String sFileName = args[5];
        String rFileName = args[6];
        InetAddress saddr = null;

        try {
            saddr = InetAddress.getByName(sNode);
        } catch (UnknownHostException ue) {
            System.out.println("Unknown Host name");
            System.exit(1);
        }

        spec.setKeyStoreFileName(keyStoreFileName);
        spec.setKeyStoreProvider(keyStoreProvider);
        spec.setSignatureAlgorithm(algorithm);

        keyPublic = spec.setSignatureKey(password);

        try {
            spec.addSignVerificationKey(saddr.getHostAddress(), keyPublic);
        } catch (KeyException ke) {
            System.out.println("Alias already exists");
        }

        Signature sig, sigV;

        try {
            sig = Signature.getInstance(algorithm);
            sigV = Signature.getInstance(algorithm);

            sig.initSign(spec.getSignatureKey(password));
            sigV.initVerify(spec.getSignVerificationKey(
		saddr.getHostAddress()));

            String text = "Dah Dah Dah.... Booh Booh Booh Signature Test!";

            sig.update(text.getBytes());

            byte[] signedVal = sig.sign();
            byte[] signature = new byte[signedVal.length + 40];

            System.arraycopy(signedVal, 0, signature, 0, signedVal.length);
            sigV.update(text.getBytes());

            if (sigV.verify(signature)) {
                System.out.println("The Signature is Good");
            } else {
                System.out.println("The Signature does not verify");
            }
        } catch (Exception e) {
            e.printStackTrace();

            return;
        }

        AuthenticationSpec specS = null;

        try {
            AuthenticationSpec.writeToFile(sFileName, spec);

            specS = AuthenticationSpec.readFromFile(sFileName);
        } catch (IOException ie) {
            return;
        }

        // Now Verify that the keys still work!.

        try {
            sig.initSign(specS.getSignatureKey(password));
            sigV.initVerify(specS.getSignVerificationKey(
		saddr.getHostAddress()));

            String text = "Dah Dah Dah.... Booh Booh Booh Signature Test!";

            sig.update(text.getBytes());

            byte[] signedVal = sig.sign();

            sigV.update(text.getBytes());

            if (sigV.verify(signedVal)) {
                System.out.println("The Signature is Still Good");
            } else {
                System.out.println("The new key Signature does not verify");
            }

            // Now write out the receiver's authentication spec.

            spec.setKeyStoreFileName(null);
            spec.setKeyStoreProvider(null);
            AuthenticationSpec.writeToFile(rFileName, spec);
        } catch (Exception ie1) {
            return;
        }
    }

    /**
     * Constructor.
     */
    public AuthenticationSpec() {
        System.out.println("In the Constructor");
    }

    /**
     * method to get Signature Key
     */
    public PrivateKey getSignatureKey(String password) {
        if (password == null) {

            // this must be a receiver

            return null;
        } 

        PrivateKey signatureKey = null;

        try {

            // read in the key store

            FileInputStream fis = new FileInputStream(getKeyStoreFileName());
            DataInputStream dis = new DataInputStream(fis);
            byte bytes[] = new byte[dis.available()];

            dis.readFully(bytes);

            ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
            KeyStore ks = KeyStore.getInstance(getKeyStoreProvider());

            if (bais == null) {
                System.out.println("keyStore " + getKeyStoreFileName() 
                                   + " could not be loaded");
            } 

            while (bais.available() > 0) {
                ks.load(bais, password.toCharArray());
            }

            signatureKey = (PrivateKey) ks.getKey("signature", 
                                                  password.toCharArray());
        } catch (Exception e) {
            e.printStackTrace();
        }

        return signatureKey;
    }

    /**
     * method to set Signature Key
     */
    public PublicKey setSignatureKey(String password) {
        CertAndKeyGen keypair = null;

        try {

            // create keystore

            KeyStore keyStore = KeyStore.getInstance(getKeyStoreProvider());

            keyStore.load(null, null);

            // The following code shamelessly stolen from keytool's source

            String sigAlgName = null;
            String keyAlgName = getSignatureAlgorithm();

            if (keyAlgName.equalsIgnoreCase("DSA")) {
                sigAlgName = "SHA1WithDSA";
            } else if (keyAlgName.equalsIgnoreCase("RSA")) {
                sigAlgName = "MD5WithRSA";
            } else {
                System.out.println("Cannot derive signature algorithm");
            }

            // make a key pair with a certificate

            keypair = new CertAndKeyGen(keyAlgName, sigAlgName);

            // and a dn

            X500Name x500Name;

            x500Name = new X500Name("JRMS", "SunLabs", "Sun", "US");

            // generate the keypair

            keypair.generate(1024);

            // make a certificate chain 1 long

            X509Certificate[] chain = new X509Certificate[1];
            int validity = 90;

            chain[0] = keypair.getSelfCertificate(x500Name, 
                                                  validity * 24 * 60 * 60);

            // add an entry for the signatureKey to the keystore

            keyStore.setKeyEntry("signature", keypair.getPrivateKey(), 
                                 password.toCharArray(), chain);

            // check it

            PrivateKey testKey = (PrivateKey) keyStore.getKey("signature", 
                    password.toCharArray());

            // write out the keystore

            FileOutputStream fos = 
                new FileOutputStream(getKeyStoreFileName());

            keyStore.store(fos, password.toCharArray());

            // check it again

            testKey = (PrivateKey) keyStore.getKey("signature", 
                                                   password.toCharArray());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // return the public key

        return keypair.getPublicKey();
    }

    /**
     * method to get signature verification Key or public key
     * 
     * @params alias - the alias of the required public key.
     * @exception KeyException - if no matching signature verification
     * key is found.
     * 
     */
    public PublicKey getSignVerificationKey(String alias) 
            throws KeyException {
        try {
            PublicKey pkey = findSignVerificationKey(alias);

            return pkey;
        } catch (KeyException ke) {
            throw new KeyException();
        }
    }

    /**
     * method to add a new signature verification key to the spec.
     * 
     * @param alias alias of the key being added to the spec.
     * @param verifyKey verification key being added to the spec.
     * 
     * @Exception KeyException when a verify key bearing the same alias
     * already exists.
     */
    public void addSignVerificationKey(String alias, PublicKey verifyKey) 
            throws KeyException {
        try {
            PublicKey vkey = findSignVerificationKey(alias);

            throw new KeyException("Key Already exists");
        } catch (KeyException ke) {
            SignVerificationKey vkey = new SignVerificationKey(alias, 
                    verifyKey);

            signVerificationKeys.add(vkey);
        }
    }

    /**
     * method to remove a verification key from the spec
     * 
     * @param alias the aliass of the verification key that is to be removed.
     * 
     */
    public void deleteSignVerificationKey(String alias) {
        for (int i = 0; i < signVerificationKeys.size(); i++) {
            try {
                SignVerificationKey vkey = 
                    (SignVerificationKey) signVerificationKeys.elementAt(i);

                if (vkey.contains(alias)) {
                    signVerificationKeys.remove(vkey);

                    return;
                }
            } catch (ArrayIndexOutOfBoundsException ae) {}
        }
    }

    /**
     * returns the name of the provider of the Signature module.
     * 
     * @return the name of the provider of the signature module.
     * 
     */
    public String getSignatureProvider() {
        return signatureProvider;
    }

    /**
     * sets the name of the provider of the Signature module.
     * 
     * @param signatureProvider the name of the provider of the
     * signature module.
     * 
     */
    public void setSignatureProvider(String signatureProvider) {
        this.signatureProvider = signatureProvider;
    }

    /**
     * returns the name of the Signature algorithm specified in the spec.
     * 
     * @return SigAlgorithm the name of the signature algorithm.
     * 
     */
    public String getSignatureAlgorithm() {
        return signatureAlgorithm;
    }

    /**
     * sets the name of the Signature algorithm in the spec.
     * 
     * @param algorithm the name of the algorithm to be set in the spec.
     * 
     */
    public void setSignatureAlgorithm(String algorithm) {
        this.signatureAlgorithm = algorithm;
    }

    /**
     * returns the name of the KeyStore file specified in the spec.
     * 
     * @return FileName the name of the KeyStore file.
     * 
     */
    public String getKeyStoreFileName() {
        return keyStoreFileName;
    }

    /**
     * sets the name of the KeyStore file in the spec.
     * 
     * @param FileName the name of the keyStore file to be set in the spec.
     * 
     */
    public void setKeyStoreFileName(String fileName) {
        keyStoreFileName = fileName;
    }

    /**
     * returns the name of the KeyStore provider specified in the spec.
     * 
     * @return ProviderName the name of the KeyStore provider.
     * 
     */
    public String getKeyStoreProvider() {
        return keyStoreProvider;
    }

    /**
     * sets the name of the KeyStore provider in the spec.
     * 
     * @param ProviderName the name of the keyStore provider to be set in 
     * the spec.
     * 
     */
    public void setKeyStoreProvider(String provider) {
        keyStoreProvider = provider;
    }

    /*
     * Writes the authentication Spec to a file.
     * 
     * @param fileName name of the file to which the spec will be stored.
     * @paranm spec the authentication spec that needs to be stored.
     * 
     */

    public static void writeToFile(String fileName, AuthenticationSpec spec) 
            throws IOException {
        FileOutputStream f = new FileOutputStream(fileName);
        ObjectOutputStream s = new ObjectOutputStream(f);

        try {
            s.writeObject(spec);
            s.close();
        } catch (IOException ie) {
            ie.printStackTrace();
        }
    }

    /**
     * Returns an instantiated authenticationSpec class by reading the
     * details stored in a file.
     * 
     * @param fileName the name of the file from which the details of
     * authentication are to be read.
     * 
     * @Exception IOException is thrown when problems in reading is detected.
     * @Exception FileNotFoundException when the specified file is not found.
     * 
     */
    public static AuthenticationSpec readFromFile(String fileName) 
            throws IOException, FileNotFoundException {
        AuthenticationSpec spec = null;
        FileInputStream f;

        f = new FileInputStream(fileName);

        ObjectInputStream s = new ObjectInputStream(f);

        try {
            spec = (AuthenticationSpec) s.readObject();
        } catch (ClassNotFoundException ce) {
            spec = null;
        }

        if (spec == null) {
            throw new IOException();
        } 

        return spec;
    }

    /*
     * Private Method to search for a certificate.
     */

    private PublicKey findSignVerificationKey(String alias) 
            throws KeyException {
        SignVerificationKey vkey = null;

        for (int i = 0; i < signVerificationKeys.size(); i++) {
            try {
                vkey = 
                    (SignVerificationKey) signVerificationKeys.elementAt(i);

                if (vkey.contains(alias)) {
                    return vkey.getKey();
                }
            } catch (ArrayIndexOutOfBoundsException ae) {
                break;
            }
        }

        throw new KeyException("No Such Verification Key");
    }
}

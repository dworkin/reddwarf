/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/**
 *
 * @author Matthew Aramah
 */
package com.sun.sgs.impl.net.ssl;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import java.security.KeyStore;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class SSLEngineFactory {

    // The SSLContext
    private static SSLContext sslContext = null;

    // The SSLEngine
    private static SSLEngine sslEngine = null;

    private static final LoggerWrapper logger = new LoggerWrapper(
            Logger.getLogger(SSLEngineFactory.class.getName()));

    // Initializes/creates the SSLContext with key material
    public static SSLEngine getEngine() {
        try {
            char[] passphrase = "123456".toCharArray();

            // First initialize the key and trust material.
            KeyStore ksKeys = KeyStore.getInstance(KeyStore.getDefaultType());
            ksKeys.load(
                    new FileInputStream("./target/classes/com/sun/sgs/impl/net/ssl/keystore"),
                    passphrase);
            KeyStore ksTrust = KeyStore.getInstance(KeyStore.getDefaultType());
            ksTrust.load(
                    new FileInputStream("./target/classes/com/sun/sgs/impl/net/ssl/truststore"),
                    passphrase);

            // KeyManager's decide which key material to use.
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ksKeys, passphrase);

            // TrustManager's decide whether to allow connections.
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ksTrust);

            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            sslEngine = sslContext.createSSLEngine();
        }
        catch (KeyStoreException kse) {
            logger.logThrow(Level.WARNING, kse,
                    "problem with keystore or truststore {0}",
                    kse.getMessage());
        }
        catch (NoSuchAlgorithmException nsae) {
            logger.logThrow(Level.WARNING, nsae,
                    "problem with manager factory {0}", nsae.getMessage());
        }
        catch (IOException ioe) {
            logger.logThrow(Level.WARNING, ioe,
                    "problem reading file {0}", ioe.getMessage());
        }
        catch (Exception e) {
            logger.logThrow(Level.WARNING, e, "there was a serious problem");
        }

        return sslEngine;
    }
}

/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.impl.net.ssl;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import java.security.KeyStore;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Properties;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;

public class SSLEngineFactory {

    // The package name
    private static final String PKG_NAME = "com.sun.sgs.impl.net.ssl";

    // The keyStore password property
    public static final String KEYSTORE_PASSWORD_PROPERTY =
            PKG_NAME + ".keystore.password";

    // The keyStore location property
    public static final String KEYSTORE_LOCATION_PROPERTY = 
            PKG_NAME + ".keystore.location";
    
    // The trustStore location property
    public static final String TRUSTSTORE_LOCATION_PROPERTY = 
            PKG_NAME + ".truststore.location";
    
    // The keyStore type property
    public static final String KEYSTORE_TYPE_PROPERTY = 
            PKG_NAME + "keystore.type";
    
    // The keyStore provider property
    public static final String KEYSTORE_PROVIDER_PROPERTY = 
            PKG_NAME + "keystore.provider";

    // The keymanager algorithm property
    public static final String KEYMANAGER_ALGORITHM_PROPERTY =
            PKG_NAME + "keymanager.algorithm";

    // The trustmanager provider property
    public static final String KEYMANAGER_PROVIDER_PROPERTY =
            PKG_NAME + "keymanager.provider";

    // The trustmanager algorithm property
    public static final String TRUSTMANAGER_ALGORITHM_PROPERTY = 
            PKG_NAME + "trustmanager.algorithm";
    
    // The trustmanager provider property
    public static final String TRUSTMANAGER_PROVIDER_PROPERTY = 
            PKG_NAME + "trustmanager.provider";

    // The client authorization property
    public static final String CLIENT_AUTHORIZATION_PROPERTY =
            PKG_NAME + "client.authorization";

    // The enabled cipher suites property
    public static final String ENABLED_CIPHER_SUITES_PROPERTY =
            PKG_NAME + "enabled.cipher.suites";
    
    // The SSLEngine
    private static SSLEngine sslEngine = null;

    // The logger for this class
    private static final LoggerWrapper logger = new LoggerWrapper(
            Logger.getLogger(SSLEngineFactory.class.getName()));
    
    /* 
     * Initializes and creates the SSLContext with key material
     */ 
    public static void initialize(Properties properties) {

        PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);

        String keyStore = properties.getProperty(KEYSTORE_LOCATION_PROPERTY);
        String trustStore = properties.getProperty(
                                                TRUSTSTORE_LOCATION_PROPERTY);
        String keyStoreType = properties.getProperty(KEYSTORE_TYPE_PROPERTY);
        String keyStoreProvider = properties.getProperty(
                                                KEYSTORE_PROVIDER_PROPERTY);
        String keyManagerAlgorithm = properties.getProperty(
                                                KEYMANAGER_ALGORITHM_PROPERTY);
        String keyManagerProvider = properties.getProperty(
                                                KEYMANAGER_PROVIDER_PROPERTY);
        String trustManagerAlgorithm = properties.getProperty(
                                            TRUSTMANAGER_ALGORITHM_PROPERTY);
        String trustManagerProvider = properties.getProperty(
                                            TRUSTMANAGER_PROVIDER_PROPERTY);

        boolean needClientAuth = wrappedProps.getBooleanProperty(
                                        CLIENT_AUTHORIZATION_PROPERTY, false);

        List<String> enabledCipherSuites = wrappedProps.getListProperty(
                ENABLED_CIPHER_SUITES_PROPERTY, String.class, "");

        String[] cipherSuites = (String[])enabledCipherSuites.toArray(
                new String[0]);

        try {
            char[] password = properties.getProperty(
                    KEYSTORE_PASSWORD_PROPERTY).toCharArray();

            // First initialize the key and trust material.
            KeyStore ksKeys = KeyStore.getInstance(keyStoreType,
                                                            keyStoreProvider);
            ksKeys.load(new FileInputStream(keyStore), password);
            KeyStore ksTrust = KeyStore.getInstance(keyStoreType,
                                                            keyStoreProvider);
            ksTrust.load(new FileInputStream(trustStore), password);

            // KeyManager's decide which key material to use.
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    keyManagerAlgorithm, keyManagerProvider);
            kmf.init(ksKeys, password);

            // TrustManager's decide whether to allow connections.
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    trustManagerAlgorithm, trustManagerProvider);
            tmf.init(ksTrust);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            sslEngine = sslContext.createSSLEngine();

            sslEngine.setNeedClientAuth(needClientAuth);
            sslEngine.setEnabledCipherSuites(cipherSuites);
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
            logger.logThrow(Level.WARNING, e,
                    "problem creating SSLContext {0}", e.getMessage());
        }
        
    }

    /*
     * Returns SSLEngine
     */
    public static SSLEngine getSSLEngine() {

        if (sslEngine == null) {
            throw new AssertionError("SSLEngine not initialized");
        }

        return sslEngine;
    }
}

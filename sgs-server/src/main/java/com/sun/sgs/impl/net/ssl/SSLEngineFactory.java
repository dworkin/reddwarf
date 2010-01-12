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
    
    // The SSLContext
    private static SSLContext sslContext = null;

    // The SSLEngine
    private static SSLEngine sslEngine = null;

    // SSLEngine properties
    private static Properties properties = null;

    // The logger for this class
    private static final LoggerWrapper logger = new LoggerWrapper(
            Logger.getLogger(SSLEngineFactory.class.getName()));
    
    /* 
     * Initializes/creates the SSLContext with key material
     */ 
    public static SSLEngine getEngine() {

        if (properties == null) {
            throw new NullPointerException("properties is null");
        }
        
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
            logger.logThrow(Level.WARNING, e,
                    "problem creating SSLContext {0}", e.getMessage());
        }
        
        return sslEngine;
    }

    /*
     * initializes properties
     */
    public static void initialize(Properties props) {
        properties = props;
    }
}

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

/**
 * Creates the singleton server side {@code SSLEngine}.
 * The {@code SSLEngineFactory.initialize} method supports the following
 * properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #KEYSTORE_PASSWORD_PROPERTY}
 *	</b></code><br>
 *
 * <dd style="padding-top: .5em">Specifies the keystore password.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #TRUSTSTORE_PASSWORD_PROPERTY}
 *	</b></code><br>
 *
 * <dd style="padding-top: .5em">Specifies the truststore password.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #KEYSTORE_LOCATION_PROPERTY}
 *	</b></code><br>
 *
 * <dd style="padding-top: .5em">Specifies the path name of the keystore in
 *      the file system.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #TRUSTSTORE_LOCATION_PROPERTY}
 *	</b></code><br>
 *
 * <dd style="padding-top: .5em">Specifies the path name of the truststore in
 *      the file system.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #KEYSTORE_TYPE_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> jks<br>
 *
 * <dd style="padding-top: .5em">Specifies the type of the keystore.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #KEYSTORE_PROVIDER_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> SunJSSE<br>
 *
 * <dd style="padding-top: .5em">Specifies the name of the provider of the
 *      keystore.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #KEYMANAGER_ALGORITHM_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> SunX509<br>
 *
 * <dd style="padding-top: .5em">Specifies the standard name of the requested
 *      keymanager algorithm.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #KEYMANAGER_PROVIDER_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> SunJSSE<br>
 *
 * <dd style="padding-top: .5em">Specifies the name of the provider of the
 *      keymanager.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #TRUSTMANAGER_ALGORITHM_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> SunX509<br>
 *
 * <dd style="padding-top: .5em">Specifies the standard name of the requested
 *      trustmanager algorithm.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #TRUSTMANAGER_PROVIDER_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> SunJSSE<br>
 *
 * <dd style="padding-top: .5em">Specifies the name of the provider of the
 *      trustmanager.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #CLIENT_AUTHENTICATION_PROPERTY}
 *	</b></code><br>
 *	<i>Default:</i> false<br>
 *
 * <dd style="padding-top: .5em">Specifies whether client authentication is
 *      required.<p>
 *
 * <dt> <i>Property:</i> <code><b>
 *	{@value #ENABLED_CIPHER_SUITES_PROPERTY}
 *	</b></code><br>
 *
 * <dd style="padding-top: .5em">Specifies the enabled cipher suites for the
 *      {@link SSLEngine}.
 * </dl> <p>
 */
public class SSLEngineFactory {

    /** The package name */
    private static final String PKG_NAME = "com.sun.sgs.impl.net.ssl";

    /** The name of the keystore keyStorePassword property */
    private static final String KEYSTORE_PASSWORD_PROPERTY =
            PKG_NAME + ".keystore.password";

    /** The name of the truststore keyStorePassword property */
    private static final String TRUSTSTORE_PASSWORD_PROPERTY =
            PKG_NAME + ".truststore.password";

    /** The name of the keystore location property */
    private static final String KEYSTORE_LOCATION_PROPERTY =
            PKG_NAME + ".keystore.location";
    
    /** The name of the truststore location property */
    private static final String TRUSTSTORE_LOCATION_PROPERTY =
            PKG_NAME + ".truststore.location";
    
    /** The name of the keystore type property */
    private static final String KEYSTORE_TYPE_PROPERTY =
            PKG_NAME + ".keystore.type";
    
    /** The name of the keystore provider property */
    private static final String KEYSTORE_PROVIDER_PROPERTY =
            PKG_NAME + ".keystore.provider";

    /** The name of the key manager algorithm property */
    private static final String KEYMANAGER_ALGORITHM_PROPERTY =
            PKG_NAME + ".keymanager.algorithm";

    /** The name of the key manager provider property */
    private static final String KEYMANAGER_PROVIDER_PROPERTY =
            PKG_NAME + ".keymanager.provider";

    /** The name of the trust manager algorithm property */
    private static final String TRUSTMANAGER_ALGORITHM_PROPERTY =
            PKG_NAME + ".trustmanager.algorithm";
    
    /** The name of the trust manager provider property */
    private static final String TRUSTMANAGER_PROVIDER_PROPERTY =
            PKG_NAME + ".trustmanager.provider";

    /** The name of the client authentication property */
    private static final String CLIENT_AUTHENTICATION_PROPERTY =
            PKG_NAME + ".client.authentication";

    /** The name of the enabled cipher suites property */
    private static final String ENABLED_CIPHER_SUITES_PROPERTY =
            PKG_NAME + ".enabled.cipher.suites";
    
    /** The SSLEngine */
    private static SSLEngine sslEngine = null;

    /** The logger for this class */
    private static final LoggerWrapper logger = new LoggerWrapper(
            Logger.getLogger(SSLEngineFactory.class.getName()));
    
    /**
     * Initializes and creates the {@code SSLContext} with key material
     *
     * @param properties SSLContext properties
     */ 
    public static synchronized void initialize(Properties properties) {

        if (sslEngine != null) {
            throw new AssertionError("SSLEngine already initialized");
        }

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
                                        CLIENT_AUTHENTICATION_PROPERTY, false);

        List<String> enabledCipherSuites = wrappedProps.getListProperty(
                ENABLED_CIPHER_SUITES_PROPERTY, String.class, "");

        String[] cipherSuites = (String[])enabledCipherSuites.toArray(
                new String[0]);

        try {
            char[] keyStorePassword = properties.getProperty(
                    KEYSTORE_PASSWORD_PROPERTY).toCharArray();

            char[] trustStorePassword = properties.getProperty(
                    TRUSTSTORE_PASSWORD_PROPERTY).toCharArray();

            KeyStore ksKeys = KeyStore.getInstance(keyStoreType,
                                                            keyStoreProvider);
            ksKeys.load(new FileInputStream(keyStore), keyStorePassword);
            KeyStore ksTrust = KeyStore.getInstance(keyStoreType,
                                                            keyStoreProvider);
            ksTrust.load(new FileInputStream(trustStore), trustStorePassword);

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    keyManagerAlgorithm, keyManagerProvider);
            kmf.init(ksKeys, keyStorePassword);

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

        logger.log(Level.CONFIG,
                    "Created SSLEngine with properties:" +
                    "\n " + KEYSTORE_LOCATION_PROPERTY + "=" + keyStore +
                    "\n " + TRUSTSTORE_LOCATION_PROPERTY + "=" + trustStore +
                    "\n " + KEYSTORE_TYPE_PROPERTY + "=" + keyStoreType +
                    "\n " + KEYSTORE_PROVIDER_PROPERTY + "=" +
                    keyStoreProvider +
                    "\n " + KEYMANAGER_ALGORITHM_PROPERTY + "=" +
                    keyManagerAlgorithm +
                    "\n " + KEYMANAGER_PROVIDER_PROPERTY + "=" +
                    keyManagerProvider +
                    "\n " + TRUSTMANAGER_ALGORITHM_PROPERTY + "=" +
                    trustManagerAlgorithm +
                    "\n " + TRUSTMANAGER_PROVIDER_PROPERTY + "=" +
                    trustManagerProvider +
                    "\n " + CLIENT_AUTHENTICATION_PROPERTY + "=" +
                    needClientAuth +
                    "\n " + ENABLED_CIPHER_SUITES_PROPERTY + "=" +
                    cipherSuites);
        
    }

    /**
     * Returns the {@code SSLEngine}.
     *
     * @return {@code sslEngine}
     */
    public static synchronized SSLEngine getSSLEngine() {

        if (sslEngine == null) {
            throw new AssertionError("SSLEngine not initialized");
        }

        return sslEngine;
    }
}

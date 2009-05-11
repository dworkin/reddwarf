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

package com.sun.sgs.impl.kernel;

import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.SequenceInputStream;
import java.io.Closeable;
import java.util.Vector;
import java.util.logging.LogManager;

/**
 * This class is used to initialize logging levels at system startup.
 * If the system property {@code java.util.logging.config.class} is set to this
 * class's name, the constructor for this class will be used to initialize
 * logging levels from several sources.
 */
public class LoggerPropertiesInit {

    /**
     * This class is instantiated if the system property 
     * {@code java.util.logging.config.class} is set to this class's
     * name.  Initialize logging levels according to the following rules.
     * In each of the rules below, the input stream from the file
     * "lib/logging.properties" from the JRE is concatenated with the
     * "source {@code InputStream}" and used
     * to set the logging levels with the 
     * {@link LogManager#readConfiguration(java.io.InputStream)} method:
     * <ul>
     * <li>If the system resource specified by the property
     * {@link BootProperties#DEFAULT_LOG_PROPERTIES} exists, <em>and</em>
     * the file specified by the system property 
     * {@code java.util.logging.config.file} exists, use the concatenation of
     * input streams of these two items (in that order) as the source
     * {@code InputStream}.</li>
     * <li>If one and only one of the resources mentioned above exists,
     * use it as the source {@code InputStream}.</li>
     * <li>If neither of the resources mentioned above exist, initialize
     * the logging levels according to the standard rules for the
     * {@link LogManager} as if neither {@code java.util.logging.config.class}
     * nor {@code java.util.logging.config.file} are set.  In other words,
     * there is no "source {@code InputStream}" and the default
     * "lib/logging.properties" file is simply used.</li>
     * </ul>
     * Note that if both resources exist as in the first scenario above,
     * any property that is specified in both resources will retain the
     * value of the property from the {@code java.util.logging.config.file}. 
     * 
     * @throws IOException if an error occurs reading the configuration
     *         from the source {@code InputStream}
     */
    public LoggerPropertiesInit() throws IOException {
        init(BootProperties.DEFAULT_LOG_PROPERTIES);
    }
    
    /**
     * This method is used by the no-argument constructor which 
     * passes the value of {@link BootProperties#DEFAULT_LOG_PROPERTIES} as
     * the value of the single {@code defaultLogProperties} parameter.  It
     * provides support for using a custom resource name to facilitate testing.
     * 
     * @param defaultLogProperties the location of the system resource used
     *        to specify logging configuration
     * @throws IOException if an error occurs reading the configuration
     *         from the source {@code InputStream}
     * @see LoggerPropertiesInit#LoggerPropertiesInit() 
     */
    private static void init(String defaultLogProperties) 
            throws IOException {
        InputStream defaultStream = null;
        InputStream resourceStream = null;
        InputStream configFileStream = null;
        SequenceInputStream combinedStream = null;
        
        try {
            defaultStream = getInputStreamFromFilename(
                System.getProperty("java.home") + File.separator +
                "lib" + File.separator + "logging.properties");
            resourceStream = ClassLoader.getSystemResourceAsStream(
                defaultLogProperties);
            configFileStream = getInputStreamFromFilename(
                System.getProperty("java.util.logging.config.file"));
        
            Vector<InputStream> streamList = new Vector<InputStream>(3);
            streamList.add(defaultStream);
            if (resourceStream != null) {
                streamList.add(resourceStream);
            } 
            if (configFileStream != null) {
                streamList.add(configFileStream);
            }

            combinedStream = new SequenceInputStream(streamList.elements());
            LogManager.getLogManager().readConfiguration(combinedStream);
        } finally {
            close(combinedStream);
            close(configFileStream);
            close(resourceStream);
            close(defaultStream);
        }
    }
    
    /**
     * Utility method that opens and returns an {@code InputStream} connected
     * to the file with the given name.
     * 
     * @param filename name of the file to get a stream
     * @return an {@code InputStream} attached to the given file or {@code null}
     *         if the file does not exist or the filename is {@code null}
     */
    private static InputStream getInputStreamFromFilename(String filename) {
        InputStream fileStream = null;
        
        try {
            if (filename != null) {
                fileStream = new FileInputStream(new File(filename));
            }
        } catch (FileNotFoundException fnfe) {
            //ignore file doesn't exist
        }
        
        return fileStream;
    }
    
    /**
     * Utility method to force close a {@code Closeable} object, ignoring any
     * exception that is thrown.
     * 
     * @param c the {@code Closeable} to close
     */
    private static void close(Closeable c) {
        try {
            if (c != null) {
                c.close();
            }
        } catch (IOException e) {
            //ignore
        }
    }
    
    /**
     * This method exists to appease Checkstyle.
     */
    void noop() {
        
    }

}

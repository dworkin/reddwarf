/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.tests.boot;

import java.util.zip.ZipFile;
import java.util.zip.ZipEntry;
import java.util.Enumeration;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.Closeable;
import java.net.URL;

import org.junit.Assert;

/**
 *
 */
class Util {
    
    private static final int BUFFER = 1024;
    
    /**
     * Copies the contents of the {@code InputStream} to the
     * {@code OutputStream}.
     * 
     * @param is
     * @param os
     * @throws java.io.IOException
     */
    public static void copy(InputStream is, OutputStream os) 
            throws IOException {
        byte[] buffer = new byte[BUFFER];
        int bytes = 0;
        while ((bytes = is.read(buffer, 0, BUFFER)) != -1) {
            os.write(buffer, 0, bytes);
        }
    }
    
    /**
     * Copies the source file to the destination file.
     * 
     * @param source
     * @param destination
     * @throws java.io.IOException
     */
    public static void copyFileToFile(File source, File destination) 
            throws IOException {
        Assert.assertTrue(source.exists());
        Assert.assertTrue(source.isFile());
        Assert.assertFalse(destination.exists());
        
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new BufferedInputStream(new FileInputStream(source));
            os = new BufferedOutputStream(new FileOutputStream(destination));
            copy(is, os);
        } finally {
            os.flush();
            os.close();
            is.close();
        }
    }
    
    /**
     * Copies the source file to the target directory.
     * 
     * @param source
     * @param targetDirectory
     * @throws java.io.IOException
     */
    public static void copyFileToDirectory(File source, File targetDirectory) 
            throws IOException {
        Assert.assertTrue(source.exists());
        Assert.assertTrue(source.isFile());
        Assert.assertTrue(targetDirectory.exists());
        Assert.assertTrue(targetDirectory.isDirectory());
        
        File destination = new File(targetDirectory, source.getName());
        Assert.assertFalse(destination.exists());
        
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new BufferedInputStream(new FileInputStream(source));
            os = new BufferedOutputStream(new FileOutputStream(destination));
            copy(is, os);
        } finally {
            os.flush();
            os.close();
            is.close();
        }
    }
    
    /**
     * Copies the source URL to the target file
     * 
     * @param source
     * @param targetDirectory
     * @throws java.io.IOException
     */
    public static void copyURLToFile(URL source, File targetFile) 
            throws IOException {
        InputStream is = null;
        OutputStream os = null;
        try {
            is = new BufferedInputStream(source.openStream());
            os = new BufferedOutputStream(new FileOutputStream(targetFile));
            copy(is, os);
        } finally {
            os.flush();
            os.close();
            is.close();
        }
    }
    
    /**
     * Recursively deletes the specified directory.
     * @param directory
     * @return
     */
    public static boolean deleteDirectory(File directory) {
        if(!directory.exists()) {
            //already deleted
            return true;
        }
        
        Assert.assertTrue(directory.exists());
        Assert.assertTrue(directory.isDirectory());
        for(File f : directory.listFiles()) {
            if(f.isDirectory()) {
                deleteDirectory(f);
            } else {
                f.delete();
            }
        }
        
        return directory.delete();
    }

    /**
     * Unzips the {@code ZipFile} file into the given directory.
     * 
     * @param file the file to unzip
     * @param directory the directory to extract the contents into
     */
    public static void unzip(ZipFile file, File directory)  
            throws IOException {
        for(Enumeration<? extends ZipEntry> e = file.entries(); 
                e.hasMoreElements();) {
            ZipEntry z = e.nextElement();
            File entry = new File(directory, z.getName());
            
            if(z.isDirectory()) {
                entry.mkdirs();
            } else {
                InputStream is = null;
                OutputStream os = null;
                try {
                    is = new BufferedInputStream(file.getInputStream(z));
                    os = new BufferedOutputStream(new FileOutputStream(entry));
                    copy(is, os);
                } finally {
                    os.flush();
                    os.close();
                    is.close();
                }
            } 
        }
    }
    
    
    /**
     * Loads the tutorial into the PDS by copying the tutorial.jar
     * file from the tutorial directory into the deploy directory.
     * This assumes that the given directory argument is the top level
     * installation directory of PDS.
     * 
     * @param directory installation directory of PDS
     */
    public static void loadTutorial(File directory) 
            throws IOException {
        File tutorial = new File(directory, "tutorial/tutorial.jar");
        File deploy = new File(directory, "deploy");
        
        copyFileToDirectory(tutorial, deploy);
    }
    
    /**
     * Loads the tutorial into the PDS by copying the tutorial.jar
     * file from the tutorial directory into the alternate deploy directory.
     * This assumes that the given directory argument is the top level
     * installation directory of PDS.
     * 
     * @param directory installation directory of PDS
     * @param deploy the deploy directory to copy the tutorial to
     */
    public static void loadTutorial(File directory, File deploy) 
            throws IOException {
        File tutorial = new File(directory, "tutorial/tutorial.jar");
        
        copyFileToDirectory(tutorial, deploy);
    }
    
    /**
     * Boots up the PDS installed in the given directory.  PDS is booted
     * with the given logging file passed as the java.util.logging.config.file
     * configuration parameter on the command line if logging is not null.
     * 
     * @param directory the directory of the PDS
     * @param logging the logging configuration file or null
     * @param args command line arguments passed to the bootloader
     * @return the PDS {@code Process}
     */
    public static Process bootPDS(File directory, File logging, String args) 
            throws IOException {
        File bootloader = new File(directory, "bin/sgs-boot.jar");
        Assert.assertTrue(bootloader.exists());

        String logParam = "";
        if(logging != null) {
            logParam = "-Djava.util.logging.config.file=" + 
                    logging.getAbsolutePath();
        }

        List<String> commandList = new ArrayList<String>();
        commandList.add("java");
        commandList.add("-jar");
        if(!logParam.equals(""))
            commandList.add(logParam);
        commandList.add(bootloader.getAbsolutePath());
        commandList.add(args);
        
        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.redirectErrorStream(true);
        
        return pb.start();
    }
    
    /**
     * Shuts down the PDS running in the given directory 
     * from the given configuration.  The 
     * configuration is either the filename given in the command arguments,
     * or the default configuration if no filename is given.
     * 
     * @param args command line arguments to pass to the stopper
     * @return the PDS stopper {@code Process}
     */
    public static Process shutdownPDS(File directory, String args) 
            throws Exception {
        File stopper = new File(directory, "bin/sgs-stop.jar");
        Assert.assertTrue(stopper.exists());
        
        List<String> commandList = new ArrayList<String>();
        commandList.add("java");
        commandList.add("-jar");
        commandList.add(stopper.getAbsolutePath());
        commandList.add(args);
        
        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.redirectErrorStream(true);
        
        Process p = pb.start();
        
        //give time for process to shutdown
        Thread.sleep(500);
        return p;
    }
    
    /**
     * Removes the sgs-boot.properties file from the default installation
     * to ensure that it is not used.
     * 
     * @param directory installation directory
     * @throws java.lang.Exception
     */
    public static void clearSGS_BOOT(File directory) throws Exception {
        File f = new File(directory, "conf/sgs-boot.properties");
        Assert.assertTrue(f.exists());
        Assert.assertTrue(f.delete());
    }
    
    /**
     * Removes the sgs-server.properties file from the default installation
     * to ensure that it is not used.
     * 
     * @param directory installation directory
     * @throws java.lang.Exception
     */
    public static void clearSGS_PROPERTIES(File directory) throws Exception {
        File f = new File(directory, "conf/sgs-server.properties");
        Assert.assertTrue(f.exists());
        Assert.assertTrue(f.delete());
    }
    
    /**
     * Removes the sgs-logging.properties file from the default installation
     * to ensure that it is not used.
     * 
     * @param directory installation directory
     * @throws java.lang.Exception
     */
    public static void clearSGS_LOGGING(File directory) throws Exception {
        File f = new File(directory, "conf/sgs-logging.properties");
        Assert.assertTrue(f.exists());
        Assert.assertTrue(f.delete());
    }
    
    /**
     * Removes all conf files from the default installation
     * to ensure that they are not used.
     * 
     * @param directory installation directory
     * @throws java.lang.Exception
     */
    public static void clearALL_CONF(File directory) throws Exception {
        clearSGS_BOOT(directory);
        clearSGS_PROPERTIES(directory);
        clearSGS_LOGGING(directory);
    }
    
    /**
     * Parse the output of the process, only returning true when each of the
     * lines of output have been seen.
     * 
     * @param p
     * @param lines
     */
    public static boolean expectLines(Process p, String... lines) 
            throws IOException {
        InputStream processOutput = p.getInputStream();
        BufferedReader processReader = new BufferedReader(
                new InputStreamReader(processOutput));
        
        return expect(processReader, false, lines);
    }
    
    /**
     * Parse the output of the process, only returning true when each of the
     * given regular expressions have been matched against the process output
     * in order.
     * 
     * @param p
     * @param lines
     */
    public static boolean expectMatches(Process p, String... lines) 
            throws IOException {
        InputStream processOutput = p.getInputStream();
        BufferedReader processReader = new BufferedReader(
                new InputStreamReader(processOutput));
        
        return expect(processReader, true, lines);
    }
    
    /**
     * Parse the output of the process.  Return true only if one of the
     * lines of output of the process matches the match parameter but the
     * same line does NOT match the noMatch parameter.
     * 
     * @param p
     * @param lines
     */
    public static boolean expectMatchNoMatch(Process p,
                                             String match,
                                             String noMatch)
            throws IOException {
        InputStream processOutput = p.getInputStream();
        BufferedReader processReader = new BufferedReader(
                new InputStreamReader(processOutput));
        
        return expectMatchNoMatch(processReader, match, noMatch);
    }
    
    /**
     * Parse the output of the file, only returning true when each of the
     * lines of output have been seen.
     * 
     * @param f
     * @param lines
     */
    public static boolean expectLines(File f, String... lines) 
            throws IOException {
        InputStream fileOutput = new FileInputStream(f);
        BufferedReader fileReader = new BufferedReader(
                new InputStreamReader(fileOutput));
        
        return expect(fileReader, false, lines);
    }
    
    /**
     * Parse the output of the file, only returning true when each of the
     * given regular expressions have been matched against the file output
     * in order.
     * 
     * @param f
     * @param lines
     */
    public static boolean expectMatches(File f, String... lines) 
            throws IOException {
        InputStream fileOutput = new FileInputStream(f);
        BufferedReader fileReader = new BufferedReader(
                new InputStreamReader(fileOutput));
        
        return expect(fileReader, true, lines);
    }
    
    private static boolean expect(BufferedReader reader, 
                                       boolean regexp,
                                       String... lines)
            throws IOException {
        String line = null;
        
        Pattern[] patterns = new Pattern[lines.length];
        if(regexp) {
            for(int i = 0; i < lines.length; i++) {
                patterns[i] = Pattern.compile(lines[i]);
            }
        }
        
        try {
            int lineNumber = 0;
            while (lineNumber < lines.length && 
                    ((line = reader.readLine()) != null)) {
                if(regexp) {
                    Matcher m = patterns[lineNumber].matcher(line);
                    if(m.find()) {
                        lineNumber++;
                    }
                } else if (!regexp && line.indexOf(lines[lineNumber]) != -1) {
                    lineNumber++;
                }
            }
            
            if(lineNumber == lines.length) {
                return true;
            } else {
                return false;
            }
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ignore) {
                
            }
        }
    }
    
    
    private static boolean expectMatchNoMatch(BufferedReader reader,
                                              String match,
                                              String noMatch)
            throws IOException {
        String line = null;
        
        Pattern patternMatch = Pattern.compile(match);
        Pattern patternNoMatch = Pattern.compile(noMatch);
        
        try {
            while (((line = reader.readLine()) != null)) {
                Matcher m = patternMatch.matcher(line);
                Matcher nm = patternNoMatch.matcher(line);
                if (m.find() && !nm.find()) {
                    return true;
                }
            }
            
            return false;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ignore) {
                
            }
        }
    }
    
    /**
     * Utility to ensure all streams for the {@code Process} are closed as
     * well as destroy the process if it is still active.
     * 
     * @param p the {@code Process} to destroy
     */
    public static void destroyProcess(Process p) {
        if (p != null) {
            p.destroy();
            Util.close(p.getErrorStream());
            Util.close(p.getInputStream());
            Util.close(p.getOutputStream());
        }
    }
    
    /**
     * Utility method to close a {@code Closeable} object.
     * 
     * @param c the {@code Closeable} to close
     */
    public static void close(Closeable c) {
        try {
            c.close();
        } catch (IOException e) {
            
        }
    }
    
    
    /**
     * Utility method used for debugging purposes.
     * Pulls all data from the input stream and dumps it to
     * standard error.
     * @param is
     */
    public static void dumpStream(InputStream is) {
        byte[] buf = new byte[2048];
        int count;
        try {
            while ((count = is.read(buf)) != -1) {
                System.err.write(buf, 0, count);
                System.err.flush();
            }
        } catch (IOException e) {
        }
    }
}

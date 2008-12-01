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
     * Copies the source file to the target directory.
     * 
     * @param source
     * @param targetDirectory
     * @throws java.io.IOException
     */
    public static void copyFile(File source, File targetDirectory) 
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
     * Recursively deletes the specified directory.
     * @param directory
     * @return
     */
    public static boolean deleteDirectory(File directory) {
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
        
        copyFile(tutorial, deploy);
    }
    
    /**
     * Boots up the PDS installed in the given directory.
     * 
     * @param directory the directory of the PDS
     * @param args command line arguments passed to the bootloader
     * @return the PDS {@code Process}
     */
    public static Process bootPDS(File directory, String args) 
            throws IOException {
        File bootloader = new File(directory, "bin/sgs-boot.jar");
        Assert.assertTrue(bootloader.exists());

        String command = "java -jar " + bootloader.getAbsolutePath() + 
                " " + args;
        List<String> commandList = Arrays.asList(command.split("\\s+"));
        
        ProcessBuilder pb = new ProcessBuilder(commandList);
        pb.redirectErrorStream(true);
        
        return pb.start();
    }
    
    /**
     * Parse the output of the process, only returning when each of the
     * lines of output have been seen.
     * 
     * @param p
     * @param lines
     */
    public static boolean expectLines(Process p, String... lines) {
        InputStream processOutput = p.getInputStream();
        BufferedReader processReader = new BufferedReader(
                new InputStreamReader(processOutput));
        String line = null;
        try {
            int lineNumber = 0;
            while (((line = processReader.readLine()) != null) &&
                    lineNumber < lines.length) {
                if(line.indexOf(lines[lineNumber]) != -1) {
                    lineNumber++;
                }
            }
            
            if(lineNumber == lines.length) {
                return true;
            } else {
                return false;
            }
        } catch (IOException e) {
            return false;
        } finally {
            p.destroy();
            try {
                if (processReader != null) {
                    processReader.close();
                }
            } catch (IOException ignore) {
                
            }
        }
    }
}

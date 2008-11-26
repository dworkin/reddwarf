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
import java.io.File;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 *
 */
class Util {
    
    private static final int BUFFER = 1024;

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
            
            String dirName = directory.getAbsolutePath();
            if(!dirName.endsWith(File.separator)) {
                dirName += File.separator;
            }
            File entry = new File(dirName + z.getName());
            
            if(z.isDirectory()) {
                entry.mkdirs();
            } else {
                InputStream is = null;
                OutputStream os = null;
                try {
                    is = new BufferedInputStream(file.getInputStream(z));
                    os = new BufferedOutputStream(new FileOutputStream(entry));

                    byte[] buffer = new byte[BUFFER];
                    int bytes = 0;
                    while ((bytes = is.read(buffer, 0, BUFFER)) != -1) {
                        os.write(buffer, 0, bytes);
                    }
                } finally {
                    os.flush();
                    os.close();
                    is.close();
                }
            } 
        }
    }
}

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

/**
 *
 */
class Util {

    /**
     * Unzips the {@code ZipFile} file into the given directory.
     * 
     * @param file the file to unzip
     * @param directory the directory to extract the contents into
     */
    public static void unzip(ZipFile file, File directory) {
        for(Enumeration<? extends ZipEntry> e = file.entries(); 
                e.hasMoreElements();) {
            ZipEntry z = e.nextElement();
            System.out.println(z.getName());
        }
    }
}

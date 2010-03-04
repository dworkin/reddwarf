/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.system;

import java.io.OutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Reads input from a given {@code InputStream} and pipes it directly to a
 * given {@code OutputStream}.
 */
public class StreamPipe implements Runnable {
    
    private static final Logger logger = 
            Logger.getLogger(StreamPipe.class.getName());
    
    private final InputStream input;
    private final OutputStream output;
    
    /**
     * Constructs a new {@code StreamPipe}.
     * 
     * @param input the input stream to read from
     * @param output the output stream to write to
     */
    public StreamPipe(InputStream input, OutputStream output) {
        this.input = input;
        this.output = output;
    }
    
    /**
     * Reads the output from the {@code InputStream} associated with this object
     * and outputs the results to the {@code OutputStream} associated with
     * this object.
     */
    public void run() {
        byte[] buf = new byte[1024];
        int count;
        try {
            while ((count = input.read(buf)) != -1) {
                output.write(buf, 0, count);
                output.flush();
            }
        } catch (IOException e) {
            logger.log(Level.FINEST, "Input closed", e);
        } 
    }

}

/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;

/** Controls how to serialize class descriptors. */
interface ClassSerialization {

    /**
     * Writes a class descriptor to an object output stream.
     *
     * @param	classDesc the class descriptor
     * @param	out the object output stream
     * @throws	IOException if an I/O error occurs
     */
    void writeClassDescriptor(
	ObjectStreamClass classDesc, ObjectOutputStream out)
	throws IOException;

    /**
     * Reads a class descriptor from an object input stream.
     *
     * @param	in the object input stream
     * @return	the class descriptor
     * @throws	ClassNotFoundException if a class referred to by the class
     *		descriptor representation cannot be found
     * @throws	IOException if an I/O error occurs
     */
    ObjectStreamClass readClassDescriptor(ObjectInputStream in)
	throws ClassNotFoundException, IOException;
}


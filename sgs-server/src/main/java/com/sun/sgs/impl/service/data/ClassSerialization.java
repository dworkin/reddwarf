/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
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
     * Checks if it is permitted to instantiate an instance of the specified
     * class.
     *
     * @param	classDesc the class descriptor
     * @throws	IOException if it is not permitted to create instances of the
     *		specified class
     */
    void checkInstantiable(ObjectStreamClass classDesc)
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


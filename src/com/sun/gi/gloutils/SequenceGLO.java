/*
 * Copyright 2006 Sun Microsystems, Inc. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * -Redistributions of source code must retain the above copyright
 * notice, this  list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 * notice, this list of conditions and the following disclaimer in
 * the documentation and/or other materials provided with the
 * distribution.
 *
 * Neither the name of Sun Microsystems, Inc. or the names of
 * contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any
 * kind. ALL EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND
 * WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT, ARE HEREBY
 * EXCLUDED. SUN AND ITS LICENSORS SHALL NOT BE LIABLE FOR ANY
 * DAMAGES OR LIABILITIES  SUFFERED BY LICENSEE AS A RESULT OF  OR
 * RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR
 * ITS DERIVATIVES. IN NO EVENT WILL SUN OR ITS LICENSORS BE LIABLE
 * FOR ANY LOST REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT,
 * SPECIAL, CONSEQUENTIAL, INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER
 * CAUSED AND REGARDLESS OF THE THEORY OF LIABILITY, ARISING OUT OF
 * THE USE OF OR INABILITY TO USE SOFTWARE, EVEN IF SUN HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 * You acknowledge that Software is not designed, licensed or
 * intended for use in the design, construction, operation or
 * maintenance of any nuclear facility.
 */

package com.sun.gi.gloutils;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import java.math.BigInteger;

/**
 */
public class SequenceGLO implements GLO {
    private static final long serialVersionUID = 1L;
    private BigInteger value;

    /**
     * Creates a SequenceGLO with the given initialValue.
     */
    private SequenceGLO(BigInteger initialValue) {
	if (initialValue == null) {
	    throw new NullPointerException("initialValue is null");
	}
	this.value = initialValue;
    }

    /**
     * Creates a SequenceGLO with the given initialValue.
     */
    private SequenceGLO(long initialValue) {
	this.value = BigInteger.valueOf(initialValue);
    }

    /**
     * Creates a SequenceGLO with an initial value of zero.
     */
    private SequenceGLO() {
	this(BigInteger.ZERO);
    }

    /**
     * Returns the current value of the sequence.
     *
     * @return the current value of the sequence
     */
    public BigInteger getValue() {
	return value;
    }

    /**
     * Increments the current value of the sequence and returns the
     * new value.
     *
     * @return the new, incremented value of the sequence
     */
    public BigInteger getNext() {
	value = value.add(BigInteger.ONE);
	return value;
    }

    /**
     * Returns a {@link GLOReference} to a SequenceGLO for the given
     * task and name.  <p>
     *
     * If necessary, creates the SequenceGLO with an initial value of
     * zero.
     *
     * @param task the {@link SimTask}
     *
     * @param name the name for this new GLO
     *
     * @return a GLOReference for a SequenceGLO for the given task and
     * name
     */
    public static GLOReference<SequenceGLO> get(SimTask task,
	    String name)
    {
	BigInteger initialValue = BigInteger.ZERO;

	GLOReference<SequenceGLO> ref =
		task.createGLO(new SequenceGLO(initialValue), name);
	if (ref == null) {
	    ref = task.findGLO(name);
	}

	return ref;
    }

    /**
     * @param task the current {@link SimTask}
     *
     * @param name the name for this new GLO
     */
    public static BigInteger getNext(SimTask task, String name) {

	GLOReference<SequenceGLO> ref = SequenceGLO.get(task, name);

	SequenceGLO seq = ref.get(task);
	return seq.getNext();
    }
}

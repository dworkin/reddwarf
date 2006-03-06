/*
 * Copyright 2006 Sun Microsystems, Inc.  All Rights Reserved.
 */

package com.sun.gi.gloutils;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import java.math.BigInteger;

/**
 * A GLO that provides a simple sequence number.  <p>
 *
 * The sequence starts at a small number (by default, zero) and is
 * incremented every time <code>SequenceGLO.getNext</code> is invoked.
 *
 * The intended usage is:
 *
 * <pre>
 * BigInteger sequenceNumber = SequenceGLO.getNext(task, name);
 * </pre> <p>
 *
 * where <code>task</code> is the current task and <code>name</code>
 * is the name of the sequence. <p>
 *
 * For applications that need to generate several sequence numbers,
 * it is more efficient to acquire a {@link GLOReference} to the SequenceGLO,
 * {@link GLOReference.get get} the underlying object and invoke
 * <code>getNext</code> on it directly: <p>
 *
 * <pre>
 * // Assumes that the GLO already exists.
 * // Do this once, to get the reference:
 * GLOReference<SequenceGLO> ref = task.findGLO(name);
 *
 * // Do this once per task, to deref the reference:
 * SequenceGLO sequence = ref.get(task);
 *
 * // Within a task, do this as often as required:
 * BigInteger next = sequence.getNext();
 * </pre> <p>
 */
public class SequenceGLO implements GLO {
    private static final long serialVersionUID = 1L;
    private BigInteger value;

    /**
     * Creates a SequenceGLO with the given initial value. <p>
     *
     * Note that because of the way the values are computed, the
     * initial value is never one of the values returned by
     * {@link #getNext}. <p>
     *
     * This constructor is not meant to be used directly.  It is
     * only meant to be used by {@link #getReference}.
     *
     * @param initialValue the starting value of the sequence
     */
    private SequenceGLO(BigInteger initialValue) {
	if (initialValue == null) {
	    throw new NullPointerException("initialValue is null");
	}
	this.value = initialValue;
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
    public static GLOReference<SequenceGLO> getReference(SimTask task,
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
     * Returns the next sequence number for the SequenceGLO with the
     * given name.
     *
     * @param task the current {@link SimTask}
     *
     * @param name the name for this new GLO
     *
     * @return the next sequence number for the SequenceGLO with the
     * given name
     */
    public static BigInteger getNext(SimTask task, String name) {
	GLOReference<SequenceGLO> ref = SequenceGLO.getReference(task, name);
	SequenceGLO seq = ref.get(task);
	return seq.getNext();
    }
}

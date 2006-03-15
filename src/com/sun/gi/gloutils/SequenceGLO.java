/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

package com.sun.gi.gloutils;

import java.math.BigInteger;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

/**
 * A GLO that provides a simple sequence number.
 * <p>
 * 
 * The sequence starts at a small number (by default, zero) and is
 * incremented every time <code>SequenceGLO.getNext</code> is invoked.
 * 
 * The intended usage is:
 * 
 * <pre>
 * BigInteger sequenceNumber = SequenceGLO.getNext(task, name);
 * </pre>
 * 
 * <p>
 * 
 * where <code>task</code> is the current task and <code>name</code>
 * is the name of the sequence.
 * <p>
 * 
 * For applications that need to generate several sequence numbers, it
 * is more efficient to acquire a {@link GLOReference} to the
 * SequenceGLO, {@link GLOReference#get get} the underlying object and
 * invoke <code>getNext</code> on it directly:
 * <p>
 * 
 * <pre>
 * // Assumes that the GLO already exists.
 * // Do this once, to get the reference:
 * GLOReference&lt;SequenceGLO&gt; ref = task.findGLO(name);
 * 
 * // Do this once per task, to deref the reference:
 * SequenceGLO sequence = ref.get(task);
 * 
 * // Within a task, do this as often as required:
 * BigInteger next = sequence.getNext();
 * </pre>
 * 
 * <p>
 */
public class SequenceGLO implements GLO {

    private static final long serialVersionUID = 1L;

    private BigInteger value;

    /**
     * Creates a SequenceGLO with the given initial value.
     * <p>
     * 
     * Note that because of the way the values are computed, the initial
     * value is never one of the values returned by {@link #getNext()}.
     * <p>
     * 
     * This constructor is not meant to be used directly. It is only
     * meant to be used by {@link #getReference}.
     * 
     * @param initialValue the starting value of the sequence
     */
    private SequenceGLO(BigInteger initialValue) {
        if (initialValue == null) {
            throw new NullPointerException("initialValue is null");
        }

	// BigIntegers are immutable, so we don't need to worry that
	// someone will use the ref to change it out from under us.

        this.value = initialValue;
    }

    /**
     * Increments the current value of the sequence and returns the new
     * value.
     * 
     * @return the new, incremented value of the sequence
     */
    public BigInteger getNext() {
        value = value.add(BigInteger.ONE);
        return value;
    }

    /**
     * Returns a {@link GLOReference} to a SequenceGLO for the given
     * task and name.
     * <p>
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
            String name) {
        BigInteger initialValue = BigInteger.ZERO;

        GLOReference<SequenceGLO> ref = task.createGLO(new SequenceGLO(
                initialValue), name);
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

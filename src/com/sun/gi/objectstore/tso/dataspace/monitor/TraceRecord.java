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

package com.sun.gi.objectstore.tso.dataspace.monitor;

import java.io.InvalidObjectException;
import java.io.ObjectStreamException;
import java.io.Serializable;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;

public abstract class TraceRecord implements Serializable {

    private static final long serialVersionUID = 1L;
    private long startTime;
    private long endTime;

    protected TraceRecord() {
        super();
    }

    /**
     * Constructs a TraceRecord with a given starting time. The ending
     * time is implicitly taken to be the time when the constructor is
     * called (which is accurate if the trace record is created
     * immediately after the operation has finished).
     * 
     * @param startTime the time when the operation was started (as
     * returned by System.currentTimeMillis())
     */
    public TraceRecord(long startTime) {
        this(startTime, System.currentTimeMillis());
    }

    public TraceRecord(long startTime, long endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * Returns the starting time of the operation for which this is a
     * trace record.
     * 
     * @return the starting time, in milliseconds since the begining of
     * the epoch, of the start of the operation
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * Returns the ending time of the operation for which this is a
     * trace record.
     * 
     * @return the ending time, in milliseconds since the begining of
     * the epoch, of the start of the operation
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * Returns the unqualified name of the class of this object.
     * <p>
     * 
     * From com.sun.neuromancer.demo1.impl.record.RecordImpl.java.
     * 
     * return the unqualified name of the class of this object.
     */
    public String getUnqualifiedClassName() {
        String name = getClass().getName();
        int i = name.lastIndexOf('.');
        return (i != -1) ? name.substring(i + 1) : name;
    }

    public String toString() {
        return getUnqualifiedClassName() + "[" + "startTime:" + getStartTime()
                + ",endTime:" + getEndTime() + "]";
    }

    /**
     * Replay the operation captured by this trace record against the
     * given {@link DataSpace}.
     * 
     * @param dataSpace the DataSpace on which to execute the operation
     * 
     * @param replayState the state of the replay, including the mapping
     * between each object identifiers in the original trace and its
     * values during replay
     */
    public abstract void replay(DataSpace dataSpace, ReplayState replayState);

    /**
     * Causes deserialization to fail if there is no data for this
     * class.
     */
    private void readObjectNoData() throws ObjectStreamException {
        throw new InvalidObjectException("no data");
    }
}

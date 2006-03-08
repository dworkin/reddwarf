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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.sun.gi.objectstore.tso.dataspace.DataSpace;

/**
 * Simple class to encapsulate the state of a trace replay.
 * <p>
 * 
 * The issue is that the results of some of the replay operations may be
 * different than they were in the trace: for example, the replay of a
 * create might return a different OID than what appeared in the trace --
 * and we need to use the <em>new</em> OID in all future ops because
 * otherwise those ops might fail (or do the wrong things).
 */
public class ReplayState {

    private final Set<String> newNames = new HashSet<String>();
    private final Map<Long, Long> oidMap = new HashMap<Long, Long>();
    private final Map<String, Long> nameMap = new HashMap<String, Long>();

    public ReplayState() {}

    /**
     * Maps an original OID to the OID to use in the replay.
     * 
     * @return <code>true</code> if the mapping is new,
     * <code>false</code> if there is already an existing mapping
     */
    public boolean setOidMap(long origOid, long newOid) {
        if (oidMap.containsKey(origOid)) {
            oidMap.put(origOid, newOid);
            System.out.println("\tXX surprising");
            return false;
        } else {
            oidMap.put(origOid, newOid);
            return true;
        }
    }

    public long getMappedOid(long origOid) {
        Long oid = oidMap.get(origOid);
        if (oid == null) {
            return DataSpace.INVALID_ID;
        } else {
            return oid;
        }
    }

    /**
     * Maps an original OID name to the OID to use in the replay.
     * 
     * @return <code>true</code> if the mapping is new,
     * <code>false</code> if there is already an existing mapping
     */
    public boolean setNameMap(String oidName, long newOid) {
        if (nameMap.containsKey(oidName)) {
            nameMap.put(oidName, newOid);
            return false;
        } else {
            nameMap.put(oidName, newOid);
            return true;
        }
    }

    public long getMappedOidByName(String name) {
        if (nameMap.containsKey(name)) {
            return nameMap.get(name);
        } else {
            return DataSpace.INVALID_ID;
        }
    }

    public synchronized boolean setNewName(String name) {
        if (newNames.contains(name)) {
            return false;
        } else {
            newNames.add(name);
            return true;
        }
    }
}

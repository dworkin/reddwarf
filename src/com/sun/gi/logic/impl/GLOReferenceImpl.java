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

package com.sun.gi.logic.impl;

import java.io.IOException;
import java.io.ObjectInputStream;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.NonExistantObjectIDException;

public class GLOReferenceImpl<T extends GLO>
        implements GLOReference<T>, GLO, Comparable
{
    private static final long serialVersionUID = 1L;

    long objID;

    transient boolean peeked;

    transient T objectCache;

    public GLOReferenceImpl(long id) {
        objID = id;
        objectCache = null;
    }

    private void initTransients() {
        peeked = false;
        objectCache = null;
    }

    private void readObject(ObjectInputStream in) throws IOException,
            ClassNotFoundException {
        in.defaultReadObject();
        initTransients();
    }

    public void delete(SimTask task) {
        try {
            task.getTransaction().destroy(objID);
        } catch (DeadlockException e) {

            e.printStackTrace();
        } catch (NonExistantObjectIDException e) {

            e.printStackTrace();
        }

    }

    public T get(SimTask task) {
        if ((objectCache == null) || (peeked == true)) {
            try {
                objectCache = (T) task.getTransaction().lock(objID);
            } catch (DeadlockException e) {

                e.printStackTrace();
            } catch (NonExistantObjectIDException e) {

                e.printStackTrace();
            }
            task.registerGLOID(objID, objectCache, ACCESS_TYPE.GET);
            peeked = false;
        }
        return objectCache;
    }

    public T peek(SimTask task) {
        if (objectCache == null) {
            try {
                objectCache = (T) task.getTransaction().peek(objID);
            } catch (NonExistantObjectIDException e) {

                e.printStackTrace();
            }
            task.registerGLOID(objID, objectCache, ACCESS_TYPE.PEEK);
            peeked = true;
        }
        return objectCache;
    }

    /**
     * shallowCopy
     * 
     * @return GLOReference
     */
    public GLOReference<T> shallowCopy() {
        return new GLOReferenceImpl(objID);
    }

    public T attempt(SimTask task) {
        if ((objectCache == null) || (peeked == true)) {
            try {
                objectCache = (T) task.getTransaction().lock(objID, false);
            } catch (DeadlockException e) {

                e.printStackTrace();
            } catch (NonExistantObjectIDException e) {

                e.printStackTrace();
            }
            if (objectCache == null) {
                return null;
            }
            task.registerGLOID(objID, objectCache, ACCESS_TYPE.ATTEMPT);// was
                                                                        // gotten
                                                                        // with
                                                                        // ATTEMPT
            peeked = false;
        }
        return objectCache;
    }

    public int compareTo(Object arg0) {
        GLOReferenceImpl other = (GLOReferenceImpl) arg0;
        if (objID < other.objID) {
            return -1;
        } else if (objID > other.objID) {
            return 1;
        } else {
            return 0;
        }
    }

    public int hashCode() {
        return (int) objID;
    }

    public boolean equals(Object other) {
        return (compareTo(other) == 0);
    }
}

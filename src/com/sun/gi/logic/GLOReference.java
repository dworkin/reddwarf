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

package com.sun.gi.logic;

/**
 * All {@link GLO}s must refer to other GLOs through GLOReferences in
 * order for the persistance mechanisms to work correctly.  If a GLO
 * has a normal Java reference to another GLO as a member, then that
 * second GLO's state becomes part of the state of the first GLO as
 * opposed to an independant object in the ObjectStore.
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface GLOReference<T extends GLO> {

    /**
     * Locks the referenced {@link GLO} for write and returns a
     * reference to an in-memory instantiation of it.  Multiple get()
     * calls on GLOReferences that reference the same GLO will only
     * cause one lock and all will return the same instance.
     * 
     * @param task The {@link SimTask} context
     * 
     * @return the in-memory instance of the GLO
     */
    T get(SimTask task);

    T get();

    /**
     * Returns a task-local copy of the referenced {@link GLO}. 
     * Changes to this local copy will <em>not</em> get stored in the
     * ObjectStore.  Peek is like get() except that the object is not
     * write locked.  Multiple calls to peek() on GLOReferences that
     * reference the same GLO will return the same task-local copy.
     * 
     * @param task The SimTask context
     * 
     * @return the task-local in memory instance of the GLO
     */
    T peek(SimTask task);

    T peek();

    /**
     * Performs a non-blocking attempts to <code>get</code> the given
     * GLO.  If the object is not locked, it is locked and returned as
     * in get().  If the object is already locked, it returns
     * <code>null</code> immediately .  Multiple calls to attempt() on
     * GLOReferences that reference the same GLO will return the same
     * task-local copy.
     * 
     * @param task The SimTask context
     * 
     * @return The in-memory instance of the GLO, or <code>null</code>.
     */
    T attempt(SimTask task);

    T attempt();

    /**
     * Removes the associated GLO from the objectstore, destroying all
     * persistence data for it.
     * 
     * @param task The SimTask context
     */
    void delete(SimTask task);

    void delete();
}

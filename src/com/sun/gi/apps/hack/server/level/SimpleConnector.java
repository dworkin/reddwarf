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


/*
 * SimpleConnector.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Mar  5, 2006	10:39:24 PM
 * Desc: 
 *
 */
package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.CharacterManager;


/**
 * This implementation of <code>Connector</code> acts as a simple, two-way
 * connection between two fixed points. Those points may lie on any two
 * <code>Level<code>s, and may be the same <code>Level</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class SimpleConnector implements Connector
{

    // the two levels
    private GLOReference<? extends Level> level1Ref;
    private GLOReference<? extends Level> level2Ref;

    // the two sets of coordinates
    private int level1X;
    private int level2X;
    private int level1Y;
    private int level2Y;

    // an internal flag tracking whether the two levels are the same
    private boolean sameLevel;

    /**
     * Creates an instance of <code>SimpleConnector</code>.
     *
     * @param level1Ref a reference to a level
     * @param level1X the x-coord on the first level
     * @param level1Y the y-coord on the first level
     * @param level2Ref a reference to another level
     * @param level2X the x-coord on the second level
     * @param level2Y the y-coord on the second level
     */
    public SimpleConnector(GLOReference<? extends Level> level1Ref,
                           int level1X, int level1Y,
                           GLOReference<? extends Level> level2Ref,
                           int level2X, int level2Y) {
        this.level1Ref = level1Ref;
        this.level2Ref = level2Ref;

        this.level1X = level1X;
        this.level2X = level2X;
        this.level1Y = level1Y;
        this.level2Y = level2Y;

        // see if these are on the same level or not ... this isn't a
        // significant optimization, but it helps clarify things in
        // the enteredConnection method
        if (level1Ref.equals(level2Ref))
            sameLevel = true;
        else
            sameLevel = false;
    }

    /**
     * Transitions the given character to the other point connected to
     * their current location.
     *
     * @param mgrRef a reference to the character's manager
     */
    public boolean enteredConnection(GLOReference<? extends CharacterManager>
                                     mgrRef) {
        handleEntered(mgrRef);

        return true;
    }

    /**
     * Figures out which end to send the character to, based on which end
     * they're on right now, and moves the character.
     *
     * @param mgrRef a reference to the character's manager
     */
    protected void handleEntered(GLOReference<? extends CharacterManager>
                                  mgrRef) {
        SimTask task = SimTask.getCurrent();

        // see if we can use the level ref info
        if (sameLevel) {
            // we make a connection on the same level, so use position
            // information to figure out which direction we're going in
            CharacterManager mgr = mgrRef.peek(task);
            if ((mgr.getLevelXPos() == level1X) &&
                (mgr.getLevelYPos() == level1Y)) {
                // we're on level1, moving to level2
                level2Ref.get(task).addCharacter(mgrRef, level2X, level2Y);
            } else {
                // we're on level2, moving to level1
                level1Ref.get(task).addCharacter(mgrRef, level1X, level1Y);
            }
        } else {
            // we connect different levels, so look at the level where the
            // character is now
            if (mgrRef.peek(task).getCurrentLevel().equals(level1Ref)) {
                // we're moving to level2
                level2Ref.get(task).addCharacter(mgrRef, level2X, level2Y);
            } else {
                // we're moving to level1
                level1Ref.get(task).addCharacter(mgrRef, level1X, level1Y);
            }
        }
    }

}

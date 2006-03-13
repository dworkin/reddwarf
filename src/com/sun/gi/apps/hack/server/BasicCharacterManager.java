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
 * BasicCharacterManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Mar  5, 2006	 3:05:49 AM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.level.Level;

import com.sun.gi.apps.hack.share.Board;


/**
 * This abstract implementation of <code>CharacterManager</code> provides
 * some of the common functionality for all managers, and is extended by
 * the managers for players and AIs.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public abstract class BasicCharacterManager implements CharacterManager
{

    // a reference to the current level
    private GLOReference<? extends Level> levelRef;

    // the position on the current level
    private int xPos;
    private int yPos;

    // a unique identifier for doing equality
    private String uniqueID;

    /**
     * Creates an instance of <code>BasicCharacterManager</code>.
     *
     * @param uniqueID a unique identifier for this instance
     */
    protected BasicCharacterManager(String uniqueID) {
        this.uniqueID = uniqueID;

        xPos = -1;
        yPos = -1;
    }

    /**
     * Returns the current level where this manager is playing.
     *
     * @return the current level
     */
    public GLOReference<? extends Level> getCurrentLevel() {
        return levelRef;
    }

    /**
     * Sets the current level.
     *
     * @param levelRef a reference to a level
     */
    public void setCurrentLevel(GLOReference<? extends Level> levelRef) {
        this.levelRef = levelRef;
    }

    /**
     * Sets the current character's position on the current level.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     */
    public void setLevelPosition(int x, int y) {
        xPos = x;
        yPos = y;
    }

    /**
     * Returns the current character's x-coordinate.
     *
     * @return the x-coordinate
     */
    public int getLevelXPos() {
        return xPos;
    }

    /**
     * Returns the current character's x-coordinate.
     *
     * @return the x-coordinate
     */
    public int getLevelYPos() {
        return yPos;
    }

    /**
     * Returns a unique representation of this manager.
     *
     * @return a <code>String</code> representation
     */
    public String toString() {
        return uniqueID;
    }

    /**
     * Compares another instance of <code>BasicCharacterManager</code>
     * against this one for equality.
     *
     * @param obj an instance of <code>BasicCharacterManager</code>
     *
     * @return true if the objects are equal, false otherwise
     */
    public boolean equals(Object obj) {
        if (! (obj instanceof CharacterManager))
            return false;

        return toString().equals(obj.toString());
    }

    /**
     * Returns a hash code for this manager, which is calculated by getting
     * the hash code on the value from <code>toString</code>.
     *
     * @return a hash code
     */
    public int hashCode() {
        return toString().hashCode();
    }

}

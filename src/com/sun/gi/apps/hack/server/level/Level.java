/*
 Copyright (c) 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 Clara, California 95054, U.S.A. All rights reserved.
 
 Sun Microsystems, Inc. has intellectual property rights relating to
 technology embodied in the product that is described in this document.
 In particular, and without limitation, these intellectual property rights
 may include one or more of the U.S. patents listed at
 http://www.sun.com/patents and one or more additional patents or pending
 patent applications in the U.S. and in other countries.
 
 U.S. Government Rights - Commercial software. Government users are subject
 to the Sun Microsystems, Inc. standard license agreement and applicable
 provisions of the FAR and its supplements.
 
 This distribution may include materials developed by third parties.
 
 Sun, Sun Microsystems, the Sun logo and Java are trademarks or registered
 trademarks of Sun Microsystems, Inc. in the U.S. and other countries.
 
 UNIX is a registered trademark in the U.S. and other countries, exclusively
 licensed through X/Open Company, Ltd.
 
 Products covered by and information contained in this service manual are
 controlled by U.S. Export Control laws and may be subject to the export
 or import laws in other countries. Nuclear, missile, chemical biological
 weapons or nuclear maritime end uses or end users, whether direct or
 indirect, are strictly prohibited. Export or reexport to countries subject
 to U.S. embargo or to entities identified on U.S. export exclusion lists,
 including, but not limited to, the denied persons and specially designated
 nationals lists is strictly prohibited.
 
 DOCUMENTATION IS PROVIDED "AS IS" AND ALL EXPRESS OR IMPLIED CONDITIONS,
 REPRESENTATIONS AND WARRANTIES, INCLUDING ANY IMPLIED WARRANTY OF
 MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NON-INFRINGEMENT,
 ARE DISCLAIMED, EXCEPT TO THE EXTENT THAT SUCH DISCLAIMERS ARE HELD TO BE
 LEGALLY INVALID.
 
 Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 California 95054, Etats-Unis. Tous droits réservés.
 
 Sun Microsystems, Inc. détient les droits de propriété intellectuels
 relatifs à la technologie incorporée dans le produit qui est décrit dans
 ce document. En particulier, et ce sans limitation, ces droits de
 propriété intellectuelle peuvent inclure un ou plus des brevets américains
 listés à l'adresse http://www.sun.com/patents et un ou les brevets
 supplémentaires ou les applications de brevet en attente aux Etats -
 Unis et dans les autres pays.
 
 Cette distribution peut comprendre des composants développés par des
 tierces parties.
 
 Sun, Sun Microsystems, le logo Sun et Java sont des marques de fabrique
 ou des marques déposées de Sun Microsystems, Inc. aux Etats-Unis et dans
 d'autres pays.
 
 UNIX est une marque déposée aux Etats-Unis et dans d'autres pays et
 licenciée exlusivement par X/Open Company, Ltd.
 
 see above Les produits qui font l'objet de ce manuel d'entretien et les
 informations qu'il contient sont regis par la legislation americaine en
 matiere de controle des exportations et peuvent etre soumis au droit
 d'autres pays dans le domaine des exportations et importations.
 Les utilisations finales, ou utilisateurs finaux, pour des armes
 nucleaires, des missiles, des armes biologiques et chimiques ou du
 nucleaire maritime, directement ou indirectement, sont strictement
 interdites. Les exportations ou reexportations vers des pays sous embargo
 des Etats-Unis, ou vers des entites figurant sur les listes d'exclusion
 d'exportation americaines, y compris, mais de maniere non exclusive, la
 liste de personnes qui font objet d'un ordre de ne pas participer, d'une
 facon directe ou indirecte, aux exportations des produits ou des services
 qui sont regi par la legislation americaine en matiere de controle des
 exportations et la liste de ressortissants specifiquement designes, sont
 rigoureusement interdites.
 
 LA DOCUMENTATION EST FOURNIE "EN L'ETAT" ET TOUTES AUTRES CONDITIONS,
 DECLARATIONS ET GARANTIES EXPRESSES OU TACITES SONT FORMELLEMENT EXCLUES,
 DANS LA MESURE AUTORISEE PAR LA LOI APPLICABLE, Y COMPRIS NOTAMMENT TOUTE
 GARANTIE IMPLICITE RELATIVE A LA QUALITE MARCHANDE, A L'APTITUDE A UNE
 UTILISATION PARTICULIERE OU A L'ABSENCE DE CONTREFACON.
*/


/*
 * Level.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Thu Mar  2, 2006	 9:22:16 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;

import com.sun.gi.apps.hack.server.CharacterManager;
import com.sun.gi.apps.hack.server.Item;

import com.sun.gi.apps.hack.share.Board;


/**
 * This interface represents a single level in a <code>Dungeon</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface Level extends GLO
{

    /**
     * The standard name prefix for all levels.
     */
    public static final String NAME_PREFIX = "level:";

    /**
     * Returns the name of this level.
     *
     * @return the name
     */
    public String getName();

    /**
     * Adds a character to this level at some random point.
     *
     * @param mgrRef a reference to the <code>CharacterManager</code> who's
     *               <code>Character</code> is joining this <code>Level</code>
     */
    public void addCharacter(GLOReference<? extends CharacterManager> mgrRef);

    /**
     * Adds a character to this level at the given location.
     *
     * @param mgrRef a reference to the <code>CharacterManager</code> who's
     *               <code>Character</code> is joining this <code>Level</code>
     * @param startX the starting x-coordinate
     * @param startY the starting y-coordinate
     * 
     * @return true upon success, otherwise false
     */
    public boolean addCharacter(GLOReference<? extends CharacterManager> mgrRef,
                             int startX, int startY);

    /**
     * Removes a character from the level. This is typically only called
     * when a character wants to remove itself directly (eg, they were
     * killed, or quit back to the lobby). Otherwise, characters are
     * removed naturally through other actions (like movement).
     *
     * @param mgrRef a reference to the <code>CharacterManager</code> who's
     *               <code>Character</code> is joining this <code>Level</code>
     */
    public void removeCharacter(GLOReference<? extends CharacterManager>
                                mgrRef);

    /**
     * Adds an item to this level at some random position.
     *
     * @param itemRef a reference to the <code>Item</code>
     */
    public void addItem(GLOReference<? extends Item> itemRef);

    /**
     * Adds an item to this level at the given position.
     *
     * @param itemRef a reference to the <code>Item</code>
     * @param startX the starting x-coordinate
     * @param startY the starting y-coordinate
     */
    public void addItem(GLOReference<? extends Item> itemRef, int startX,
                        int startY);

    /**
     * Returns a snapshot (ie, static) view of the level.
     *
     * @return a snapshot of the board
     */
    public Board getBoardSnapshot();

    /**
     * Tries to move the given character in the given direction
     *
     * @param mgr the manager for the <code>Character</code> that is trying
     *            to move
     * @param direction the direction in which the character wants to move
     *
     * @return true if we moved in the requested direction, false otherwise
     */
    public boolean move(CharacterManager mg, int direction);

    /**
     * Tries to take items at the character's current location.
     *
     * @param mgr the manager for the <code>Character</code> that is trying
     *            to take the items
     *
     * @return true if we took something, false otherwise
     */
    public boolean take(CharacterManager mgr);

}

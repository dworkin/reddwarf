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
 * Tile.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Mar  4, 2006	 9:20:53 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLOReference;

import com.sun.gi.apps.hack.server.CharacterManager;
import com.sun.gi.apps.hack.server.Item;

import com.sun.gi.apps.hack.server.level.LevelBoard.ActionResult;

import java.io.Serializable;


/**
 * This interface defines a single square on a board. It is used to maintain
 * internal state about what's in a level. Its is not a <code>GLO</code>
 * because it is private state for <code>GLO</code>s.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public interface Tile extends Serializable
{

    /**
     * Returns the identifier for this tile. Typically this is the sprite
     * identifier for this space on the board.
     *
     * @return the tile's identifier
     */
    public int getID();

    /**
     * Returns a stack of identifiers, specifying everything on this
     * <code>Tile</code>. The the zeroeith index is always the same value
     * as calling <code>getID</code>. If there is am item on this tile,
     * it is in the next index, and if there is a character on this
     * tile, it always appears last. There may be multiple items or
     * characters on a tile, depending on implementation.
     *
     * @return the set of identifiers for the things at this space
     */
    public int [] getIdStack();

    /**
     * Returns whether or not this space could be entered by the given
     * character. Note that this only tests if the tile itself is passable.
     * If there is a character or item on this space that would block
     * access, it will still return true. If you want to test for the ability
     * to actually move to the space in its current state, use
     * <code>canOccupy</code>.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the tile is passable
     */
    public boolean isPassable(GLOReference<? extends CharacterManager> mgrRef);

    /**
     * Returns whether or not this tile, in its current state, can be
     * occupied by the given character. This takes into account the
     * passability of the tile as well as anything that might currently
     * occupy it. If you just want a simple test for tile passability,
     * use <code>isPassable</code>.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character can occupy this tile
     */
    public boolean canOccupy(GLOReference<? extends CharacterManager> mgrRef);

    /**
     * Adds the given character to this tile if possible.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character was added successfully
     */
    public boolean addCharacter(GLOReference<? extends CharacterManager>
                                mgrRef);

    /**
     * Removes the given character from this tile, if and only if this
     * character is already on this tile.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character was removed successfully
     */
    public boolean removeCharacter(GLOReference<? extends CharacterManager>
                                   mgrRef);

    /**
     * Adds the given item to this tile if possible.
     *
     * @param itemREf the manager for the item
     *
     * @return whether or not the item was added successfully
     */
    public boolean addItem(GLOReference<? extends Item> itemRef);

    /**
     * Removes the given item from this tile, if and only if this item
     * is already on this tile.
     *
     * @param itemRef the manager for the item
     *
     * @return whether or not the item was removed successfully
     */
    public boolean removeItem(GLOReference<? extends Item> mgrRef);

    /**
     * Test to move the given character to this tile. Note that this does
     * not actually move the character, since doing so requires knowledge
     * of the other tiles with which the character is interacting. This
     * method does test that the move can be done, and does handle any
     * interactions like moving the character to a new level or attacking
     * another character.
     *
     * @param characterManager the manager for a character
     *
     * @return the result of making the move
     */
    public ActionResult moveTo(CharacterManager characterManager);

    /**
     * Tries to take an item on this tile. Unlike <code>moveTo</code>,
     * this actually will remove items from the tile if they are
     * successfully taken.
     *
     * @param characterManager the manager for a character
     *
     * @return the result of getting an item
     */
    public ActionResult getItem(CharacterManager characterManager);

}

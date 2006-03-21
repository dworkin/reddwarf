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
 * ConnectorTile.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Mar  4, 2006	 9:24:54 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.CharacterManager;
import com.sun.gi.apps.hack.server.Item;

import com.sun.gi.apps.hack.server.level.LevelBoard.ActionResult;

import java.util.ArrayList;


/**
 * This implementation of <code>Tile</code> is used to handle interaction
 * with <code>Connector</code>s. If you walk onto this tile, you are moved
 * based on the <code>Connector</code>. This tile allows any number of
 * characters to occupy it so no one can block other's access. It also
 * ignores all interaction, so you can't collide with (fight) other
 * characters while on a connection point. This means that when you first
 * arrive somewhere, you're safe until you step off the connection point.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class ConnectorTile implements Tile
{

    // the id of this tile
    private int id;

    // a reference to the connector
    private GLOReference<? extends Connector> connectorRef;

    // the characters currently on this tile
    private ArrayList<GLOReference<? extends CharacterManager>> characters;

    /**
     * Creates an instance of <code>ConnectorTile</code>.
     *
     * @param id the tile's identifier
     * @param connectorRef a reference to the <code>Connector</code>
     */
    public ConnectorTile(int id,
                         GLOReference<? extends Connector> connectorRef) {
        this.id = id;
        this.connectorRef = connectorRef;

        characters = new ArrayList<GLOReference<? extends CharacterManager>>();
    }

    /**
     * Returns the identifier for this tile.
     *
     * @return the tile's identifier
     */
    public int getID() {
        return id;
    }

    /**
     * Always returns true, because connection points are, by definition,
     * passable.
     *
     * @param mgrRef the manager for a character
     *
     * @return true
     */
    public boolean isPassable(GLOReference<? extends CharacterManager>
                              mgrRef) {
        return true;
    }

    /**
     * Always returns true, because connection points are, by definition,
     * spaces you can occupy.
     *
     * @param mgrRef the manager for a character
     *
     * @return true
     */
    public boolean canOccupy(GLOReference<? extends CharacterManager> mgrRef) {
        return true;
    }

    /**
     * Returns a stack of identifiers, specifying everything on this
     * <code>Tile</code>.
     *
     * @return the set of identifiers for the things at this space
     */
    public int [] getIdStack() {
        SimTask task = SimTask.getCurrent();
        int [] ids = new int[characters.size() + 1];
        int i = 1;

        // this tile can't have items, so we just make a stack of all
        // the characters, where the top-most will probably obscure all
        // other characters
        for (GLOReference<? extends CharacterManager> mgrRef : characters)
            ids[i++] = mgrRef.peek(task).getCurrentCharacter().getID();

        // the first element must always be the tile itself
        ids[0] = getID();

        return ids;
    }

    /**
     * Adds the given character to this tile if possible. This should always
     * succeed.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character was added successfully
     */
    public boolean addCharacter(GLOReference<? extends CharacterManager>
                                mgrRef) {
        return characters.add(mgrRef);
    }

    /**
     * Removes the given character from this tile, if and only if this
     * character is already on this tile.
     *
     * @param mgrRef the manager for a character
     *
     * @return whether or not the character was removed successfully
     */
    public boolean removeCharacter(GLOReference<? extends CharacterManager>
                                   mgrRef) {
        return characters.remove(mgrRef);
    }

    /**
     * Always returns false, since you can't have items on this tile.
     *
     * @param itemRef the manager for the item
     *
     * @return false
     */
    public boolean addItem(GLOReference<? extends Item> itemRef) {
        return false;
    }

    /**
     * Always returns false, since you can't have items on this tile.
     *
     * @param itemRef the manager for the item
     *
     * @return false
     */
    public boolean removeItem(GLOReference<? extends Item> itemRef) {
        return false;
    }

    /**
     * Test to move the given character to this tile. Typically this results
     * in moving the character to a new point in the game based on the
     * <code>Connector</code>, but since <code>Connector</code> can choose
     * whether or not to allow characters to enter (eg, look at
     * <code>PlayerConnector</code>), this isn't always true.
     *
     * @param characterManager the manager for a character
     *
     * @return the result of making the move
     */
    public ActionResult moveTo(CharacterManager characterManager) {
        // this ignores any characters on this space, and simply sends
        // the moving character into the connector
        if (connectorRef.get(SimTask.getCurrent()).
            enteredConnection(characterManager.getReference()))
            return ActionResult.CHARACTER_LEFT;
        else
            return ActionResult.FAIL;
    }

    /**
     * This always fails, since no items are allowed on this tile.
     *
     * @param characterManager the manager for a character
     *
     * @return <code>ActionResult.FAIL</code>
     */
    public ActionResult getItem(CharacterManager characterManager) {
        return ActionResult.FAIL;
    }

}

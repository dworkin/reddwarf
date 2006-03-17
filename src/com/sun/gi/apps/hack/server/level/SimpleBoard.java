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
 * SimpleBoard.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sat Mar  4, 2006	10:51:54 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server.level;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.CharacterManager;
import com.sun.gi.apps.hack.server.Item;

import java.io.IOException;
import java.io.StreamTokenizer;

import java.util.Set;
import java.util.HashSet;
import java.util.logging.Logger;


/**
 * This is a simple implementation of <code>LevelBoard</code> that is used
 * as the default mechanism to store level state. Note that this class is
 * not a <code>GLO</code>. It must be managed as private state with something
 * that is a <code>GLO</code>.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class SimpleBoard implements LevelBoard
{

    private static Logger log =
	    Logger.getLogger("com.sun.gi.apps.hack.server.level");
    static boolean debug = true;

    // the dimension of this board
    private int width;
    private int height;

    // whether or not this board is dark
    private boolean isDark;

    // an array of Tiles, which is where we store the state
    private Tile [][] tiles;

    /**
     * Creates a new instance of <code>SimpleBoard</code> from the given
     * tokenized stream.
     *
     * @param stok a <code>StreamTokenizer</code> that provides the board
     * @param impassableSprites the set of identifiers that are impassable
     *
     * @throws IOException if the stream isn't formatted correctly
     */
    public SimpleBoard(StreamTokenizer stok, Set<Integer> impassableSprites)
        throws IOException
    {
        // get the width and height
        stok.nextToken();
        width = (int)(stok.nval);
        stok.nextToken();
        height = (int)(stok.nval);

        // get whether it's dark
        stok.nextToken();
        isDark = stok.sval.equals("true");

        // create the grid for our tiles
        tiles = new Tile[width][height];
        
        // loop through the remaining data, creating the tiles
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                stok.nextToken();
                int id = (int)(stok.nval);
                if (impassableSprites.contains(id))
                    tiles[x][y] = new ImpassableTile(id);
                else
                    tiles[x][y] = new PassableTile(id);
            }
        }

	consistent();
    }

    /**
     * Sets the given space as a connector.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param connectorRef a reference to the <code>Connector</code>
     */
    public void setAsConnector(int x, int y,
                               GLOReference<? extends Connector>
                               connectorRef) {
        Tile tile = tiles[x][y];
        tiles[x][y] = new ConnectorTile(tile.getID(), connectorRef);
    }

    /**
     * Returns the width of this board.
     *
     * @return the board's width
     */
    public int getWidth() {
        return width;
    }

    /**
     * Returns the height of this board.
     *
     * @return the board's height
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the stack of identifiers at the given position.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     *
     * @return the set of identifiers at this space
     */
    public int [] getAt(int x, int y) {

	consistent();
	int[] rc = tiles[x][y].getIdStack();
	consistent();
        return rc;
    }

    /**
     * Returns whether or not this board is dark.
     *
     * @return true if this board is dark, false otherwise
     */
    public boolean isDark() {
        return isDark;
    }

    /**
     * Tries to add a character at the given location. This board only
     * allows one character per space.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the character's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean addCharacterAt(int x, int y,
                                  GLOReference<? extends CharacterManager>
                                  mgrRef) {
        
	consistent();
	boolean rc = tiles[x][y].addCharacter(mgrRef);
	consistent();
        return rc;
    }

    /**
     * Tries to remove a character from the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the character's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean removeCharacterAt(int x, int y,
                                     GLOReference<? extends CharacterManager>
                                     mgrRef) {
	consistent();
	boolean rc = tiles[x][y].removeCharacter(mgrRef);
	consistent();
        return rc;
    }

    /**
     * Tries to add an item at the given location. This board only allows
     * one item per space.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the item's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean addItemAt(int x, int y,
                             GLOReference<? extends Item> itemRef) {
	consistent();
	boolean rc = tiles[x][y].addItem(itemRef);
	consistent();
        return rc;
    }

    /**
     * Tries to remove an item from the given location.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the item's manager
     *
     * @return true if the operation succeeded, false otherwise
     */
    public boolean removeItemAt(int x, int y,
                                GLOReference<? extends Item> itemRef) {
	consistent();
	boolean rc = tiles[x][y].removeItem(itemRef);
	consistent();
        return rc;
    }

    /**
     * Tests to see if a move would be possible to the given location for
     * the given character.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgrRef a reference to the character's manager
     *
     * @return true if the operation would succeed, false otherwise
     */
    public boolean testMove(int x, int y,
                            GLOReference<? extends CharacterManager> mgrRef) {
	consistent();
	boolean rc = tiles[x][y].isPassable(mgrRef);
	consistent();
        return rc;
    }

    /**
     * Moves the given character to the given location. The character must
     * alredy be on the board through a call to <code>addCharacterAt</code>.
     *
     * @param x the x-coordinate
     * @param y the y-coordinate
     * @param mgr the character's manager
     *
     * @return the result of attempting the move
     */
    public ActionResult moveTo(int x, int y, CharacterManager mgr) {

	consistent();

        // see if the space is passable
        if (! testMove(x, y, mgr.getReference()))
            return ActionResult.FAIL;

        // try to move onto that space, but remember the previous position
        // since we'll need it if the player left
        int oldX = mgr.getLevelXPos();
        int oldY = mgr.getLevelYPos();
        ActionResult result = tiles[x][y].moveTo(mgr);

        // if this didn't result in sucess, then return the result (note
        // that this could still have been a "successful" move, since
        // this could have been an attack, a connection somewhere else,
        // etc.) ...
        if (result != ActionResult.SUCCESS) {
            // before returning, check if the result was CHARACTER_LEFT,
            // in which case we remove them from the board (and we'll use
            // the pre-move location, just in case the character now
            // thinks it has a new location somewhere)
            if (result == ActionResult.CHARACTER_LEFT)
                tiles[oldX][oldY].removeCharacter(mgr.getReference());

	    consistent();
            return result;
        }

	consistent();

        // ...but if we succeeded, then move the character...
        tiles[oldX][oldY].removeCharacter(mgr.getReference());
        tiles[x][y].addCharacter(mgr.getReference());

	consistent();
        // ...and return success
        return ActionResult.SUCCESS;
    }

    /**
     * Tries to take an item on this tile. Unlike <code>moveTo</code>,
     * this actually will remove items from the tile if they are
     * successfully taken.
     *
     * @param characterManager the manager for a character
     *
     * @return the result of getting an item
     */
    public ActionResult getItem(int x, int y, CharacterManager mgr) {
	consistent();

        ActionResult result = tiles[x][y].getItem(mgr);

	consistent();

        if (result == ActionResult.CHARACTER_LEFT)
            tiles[x][y].removeCharacter(mgr.getReference());

	consistent();

        return result;
    }

    public boolean consistent() {
	if (debug) {
	    return true;
	}

	Set<Integer> itemsSeen = new HashSet<Integer>();
	boolean passed = true;

	for (int x = 0; x < width; x++) {
	    for (int y = 0; y < height; y++) {
		int[] items = tiles[x][y].getIdStack();
		for (int item : items) {
		    if (itemsSeen.contains(item)) {
			log.warning("item " + item + " is on two tiles.");
			(new Exception()).printStackTrace();
			passed = false;
		    }
		    itemsSeen.add(item);
		}
	    }
	}

	return passed;
    }
}

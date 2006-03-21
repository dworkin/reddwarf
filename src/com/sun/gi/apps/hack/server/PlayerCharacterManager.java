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

package com.sun.gi.apps.hack.server;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

import com.sun.gi.apps.hack.server.level.Level;

import com.sun.gi.apps.hack.share.Board;
import com.sun.gi.apps.hack.share.BoardSpace;

import java.util.Collection;
import java.util.HashMap;
import java.util.Set;


/**
 * This is an implementation of <code>CharacterManager</code> used to manage
 * <code>PlayerCharacter</code>s. This manager can contain any number of
 * characters, though only one is ever the current one playing.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class PlayerCharacterManager extends BasicCharacterManager
{

    // a self-reference
    private GLOReference<? extends CharacterManager> selfRef;

    // a reference to the owning player
    private GLOReference<Player> playerRef;

    // the characters currently owned by this manager
    private HashMap<String,PlayerCharacter> characterMap;

    // the currently playing character
    private PlayerCharacter currentCharacter;

    /**
     * Creates an instance of <code>PlayerCharacterManager</code>.
     *
     * @param playerRef a reference to the owning <code>Player</code>
     */
    public PlayerCharacterManager(GLOReference<Player> playerRef) {
        super("player:" + playerRef.peek(SimTask.getCurrent()).getName());

        this.playerRef = playerRef;

        characterMap = new HashMap<String,PlayerCharacter>();
        currentCharacter = null;
    }

    /**
     * Returns a reference to the <code>Player</code> that owns this manager.
     *
     * @return the owning <code>Player</code>
     */
    public GLOReference<Player> getPlayerRef() {
        return playerRef;
    }

    /**
     * Sets our self-reference.
     *
     * @param selfRef the self-reference
     */
    public void setRef(GLOReference<? extends CharacterManager> selfRef) {
        this.selfRef = selfRef;
    }

    /**
     * Returns a reference to this manager.
     *
     * @return a self-reference
     */
    public GLOReference<? extends CharacterManager> getReference() {
        return selfRef;
    }

    /**
     * Sets the current character, if the given name is known.
     *
     * @param characterName the name of the character
     *
     * @return true if the current character was set, false otherwise
     */
    public boolean setCurrentCharacter(String characterName) {
        currentCharacter = characterMap.get(characterName);

        return (currentCharacter != null);
    }

    /**
     * Returns the current character being played through this manager.
     *
     * @return the current character
     */
    public Character getCurrentCharacter() {
        return currentCharacter;
    }

    /**
     * Returns the number of characters managed by this class.
     *
     * @return the number of manager characters
     */
    public int getCharacterCount() {
        return characterMap.size();
    }

    /**
     * Returns the characters managed by this class.
     *
     * @return the managed characters
     */
    public Collection<PlayerCharacter> getCharacters() {
        return characterMap.values();
    }

    /**
     * Returns the names of the characters managed by this class.
     *
     * @return the names of the managed characters 
     */
    public Set<String> getCharacterNames() {
        return characterMap.keySet();
    }

    /**
     * Adds a character to this manager.
     *
     * @param character the character to add
     */
    public void addCharacter(PlayerCharacter character) {
        characterMap.put(character.getName(), character);
    }

    /**
     * Tries to remove the given sharacter from the manager.
     *
     * @param name the character to remove
     */
    public void removeCharacter(String name) {
        characterMap.remove(name);
    }

    /**
     * Sends the given board to the player.
     *
     * @param board the board to send
     */
    public void sendBoard(Board board) {
        SimTask task = SimTask.getCurrent();
        playerRef.peek(task).sendBoard(task, board);
    }

    /**
     * Sends space updates to the player.
     *
     * @param updates the updates to send
     */
    public void sendUpdate(Collection<BoardSpace> updates) {
        SimTask task = SimTask.getCurrent();
        playerRef.peek(task).sendUpdate(task, updates);
    }

    /**
     * Sends the current character's stats to the player.
     */
    public void sendCharacter() {
        SimTask task = SimTask.getCurrent();
        playerRef.peek(task).sendCharacter(task, currentCharacter);
    }

}

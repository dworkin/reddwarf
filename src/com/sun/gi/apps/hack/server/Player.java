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
 * Player.java
 *
 * Created by: seth proctor (stp)
 * Created on: Mon Feb 20, 2006	 4:59:43 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.server;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;

import com.sun.gi.apps.hack.server.level.Level;

import com.sun.gi.apps.hack.share.Board;
import com.sun.gi.apps.hack.share.BoardSpace;

import java.nio.ByteBuffer;

import java.util.Collection;


/**
 * This class represents a single player (user) in the system. Each player
 * may have several characters associated with them, although typically a
 * player only plays one character at a time. There is never more than one
 * <code>Player</code> for each user, and the user may not be logged in
 * more than once.
 * <p>
 * In addition to managing the details about a player (their name, characters,
 * channel, current uid, etc.), this is also the point where all incoming
 * messages from the client arrive. This means that message receipt and
 * processing is all done while blocking on the <code>Player</code> rather
 * than some general processing logic. This model helps distribute
 * synchronization and also protect against a player trying to disrupt
 * the system.
 *
 * @since 1.0
 * @author Seth Proctor
 */
public class Player implements SimUserDataListener
{

    /**
     * The standard namespace prefix for all players.
     */
    public static final String NAME_PREFIX = "player:";

    // notes whether or not the player is current logged in and playing
    private boolean playing;

    // the user's name (login)
    private String name;

    // the uid currently assigned to this player
    private UserID currentUid;

    // a reference to this player glo
    private GLOReference<Player> selfRef = null;

    // the channel that this player is currently using
    private ChannelID channel;

    // the game the user is currently playing, and its message handler
    private GLOReference<? extends Game> gameRef;
    private MessageHandler messageHandler = null;

    // this player's character manager
    private GLOReference<PlayerCharacterManager> characterManagerRef;

    /**
     * Creates a <code>Player</code> instance.
     *
     * @param name the player's name
     */
    private Player(String name) {
        playing = false;
        channel = null;
        currentUid = null;
        this.name = name;
    }

    /**
     * Looks for an existing instance of <code>Player</code> for the given
     * name, and creates one if an instance doesn't already exist. This
     * also takes care of registering the player.
     *
     * @param name the account name of the user
     *
     * @return a reference to the <code>Player</code> with the given name
     */
    public static GLOReference<Player> getInstance(String name) {
        SimTask task = SimTask.getCurrent();

        // try to lookup the existing Player
        GLOReference<Player> playerRef = task.findGLO(NAME_PREFIX + name);

        // if it didn't already exist, then let's create it
        if (playerRef == null) {
            playerRef = task.createGLO(new Player(name), NAME_PREFIX + name);
            playerRef.get(task).setupCharacterManager(playerRef);
        }

        return playerRef;
    }

    /**
     * FIXME: right now, most of this is just for testing, and will be
     * removed when we can do character creation on the client
     */
    private void setupCharacterManager(GLOReference<Player> ref) {
        // FIXME: just for testing
        com.sun.gi.apps.hack.share.CharacterStats stats =
            new com.sun.gi.apps.hack.share.CharacterStats("seth", 20, 20, 20, 20, 20, 20, 50, 50);
        com.sun.gi.apps.hack.share.CharacterStats stats2 =
            new com.sun.gi.apps.hack.share.CharacterStats("strongbad", 20, 20, 5, 20, 20, 10, 50, 50);

        PlayerCharacterManager mgr = new PlayerCharacterManager(ref);
        //mgr.setRef(characterManagerRef);
        mgr.addCharacter(new PlayerCharacter(ref, 41, stats));
        mgr.addCharacter(new PlayerCharacter(ref, 3, stats2));
        mgr.setCurrentCharacter("seth");
        characterManagerRef = SimTask.getCurrent().createGLO(mgr);
        characterManagerRef.get(SimTask.getCurrent()).
            setRef(characterManagerRef);   
        
        //characterManagerRef =
        //SimTask.getCurrent().createGLO(new PlayerCharacterManager(ref));
        //characterManagerRef.get(SimTask.getCurrent()).
        //setRef(characterManagerRef);        
    }

    /**
     * Returns whether this <code>Player</code> is currently logged in and
     * playing.
     *
     * @return true if the player is logged in, false otherwise
     */
    public boolean isPlaying() {
        return playing;
    }

    /**
     * Returns the user's name.
     *
     * @return the user's name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the <code>CharacterManager</code> that this <code>Player</code>
     * uses to manage their <code>Character</code>s. A <code>Player</code> may
     * only play as one <code>Character</code> at a time.
     *
     * @return the character manager
     */
    public GLOReference<PlayerCharacterManager> getCharacterManager() {
        return characterManagerRef;
    }

    /**
     * Sets the current <code>UserID</code> for this <code>Player</code>,
     * which changes from session to session. Typically this is called
     * when the player first logs again, and not again until the player
     * logs out and logs back in.
     *
     * @param uid the player's user identifier
     */
    public void setCurrentUid(UserID uid) {
        this.currentUid = uid;
    }

    /**
     * Returns this <code>Player</code>'s current <code>UserID</code>. Note
     * that this is only meaningful if <code>isPlaying</code> returns
     * true. Otherwise this will return null.
     *
     * @return the current user identifier, or null if the player is not
     *         currently playing
     */
    public UserID getCurrentUid() {
        return currentUid;
    }

    /**
     * Returns a reference that points back to this <code>Player</code>.
     *
     * @return a reference to this player
     */
    public GLOReference<Player> getReference() {
        if (selfRef == null)
            selfRef = SimTask.getCurrent().findGLO("player:" + name);

        return selfRef;
    }

    /**
     * Moves the player into the referenced <code>Game</code>. This causes
     * the <code>Player</code> to leave the game they are currently playing
     * (if they are currently playing a game) and notify the new game that
     * they are joining. If the reference provided is null, then this
     * <code>Player</code> is removed from the current game and sets itself
     * as not playing any games.
     * <p>
     * When the <code>Player</code> is first started, it is not playing any
     * games. In practice, this method is only called with a value of null
     * when the associated client logs out of the server.
     *
     * @param gameRef a reference to the new <code>Game</code>, or null
     *                if the player is only being removed from a game
     */
    public void moveToGame(GLOReference<? extends Game> gameRef) {
        SimTask task = SimTask.getCurrent();
        
        // if we were previously playing a game, leave it
        if (isPlaying()) {
            this.gameRef.get(task).leave(this);
            characterManagerRef.get(task).setCurrentLevel(null);
            playing = false;
        }

        // if we got moved into a valid game, then make the migration
        if (gameRef != null) {
            playing = true;

            // keep track of the new game...
            this.gameRef = gameRef;

            // ...and handle joining the new game
            Game game = gameRef.get(task);
            messageHandler = game.createMessageHandler();
            game.join(this);
        }

        // if we're no longer playing, then our user id is no longer valid
        if (! playing)
            this.currentUid = null;
    }

    /**
     * This is used to handle leaving dungeons before we can actually call
     * the <code>moveToGame</code> (eg, when we die). It handles multiple
     * calls while on the same level cleanly (which is done by paranoid
     * checking when leaving dungeons).
     */
    public void leaveCurrentLevel() {
        SimTask task = SimTask.getCurrent();
        CharacterManager cm = characterManagerRef.get(task);
        GLOReference<? extends Level> levelRef = cm.getCurrentLevel();

        if (levelRef != null) {
            levelRef.get(task).removeCharacter(cm.getReference());
            cm.setCurrentLevel(null);
        }
    }
    
    /**
     * Called when the player joins a channel. In this system, the player
     * only joins a new channel in the context of joining a new game, and
     * the player is never on more than one channel.
     *
     * @param cid the new channel
     * @param uid the user id, which is always this <code>Player</code>'s
     *            current uid
     */
    public void userJoinedChannel(ChannelID cid, UserID uid) {
        this.channel = cid;
    }

    /**
     * Called when the player leaves a channel. In this system, the player
     * leaves a channel each time they leave a game, and is always joined
     * into another channel unless they are logging out.
     * 
     * @param cid the channel being left
     * @param uid the user id, which is always this <code>Player</code>'s
     *            current uid
     */
    public void userLeftChannel(ChannelID cid, UserID uid) {
        // we just ignore this message, since we're always part of exactly
        // one channel, so we don't need to consume this message
    }

    /**
     * Called when data arrives from the a user. In this case, this is called
     * any time the client associated with this <code>Player</code> sends
     * a message directly to the server (ie, not a broadcast message like a
     * chat comment). This method, therefore, is the handler for all client
     * messages, and so we hold the lock on the <code>Plyer</code> and not
     * some more generally shared logic while we're processing messages.
     * <p>
     * Note that this method only gets called when the client sends messages
     * via the <code>ClientConnectionManager.sendToServer</code> method. For
     * details about broadcast messages, see this class' implementation of
     * <code>dataArrivedFromChannel</code>
     *
     * @param uid the user id, which is always this <code>Player</code>'s
     *            current uid
     * @param data the message
     */
    public void userDataReceived(UserID uid, ByteBuffer data) {
        // due to a temporary bug in the system, if we get requeued due to
        // a DeadlockException, our data buffer is in the wrong place, so
        // we rewind it here to be safe
        data.rewind();

        // call the message handler to interpret the message ... note that the
        // proxy model here means that we're blocking the player, and not the
        // game itself, while we're handling the message
        messageHandler.handleMessage(this, data);
    }

    /**
     * Called when messages arrive on the channel from the client.
     * <p>
     * Note that this method only gets called when the client sends messages
     * via the <code>ClientChannel</code> inteface (either broadcast or
     * direct messages), and only then if we're eavesdropping on the channel.
     * Because this would require many locks be taken on this
     * <code>Player</code> (the <code>ClientChannel</code> interface in this
     * game is used for chat messages), and because we don't care about
     * the client's cross talk from the server point of view, we don't
     * eavesdrop on the channel, so this method is never called. See this
     * class' implementation of <code>userDataReceived</code> for details
     * on how we handle messages sent directly from the client.
     */
    public void dataArrivedFromChannel(ChannelID id, UserID from,
                                       ByteBuffer data) {
        // this method is never called

        // NOTE: see comment in userDataReceived about rewinding the data
    }

    /**
     * Sends a complete <code>Board</code> to the client.
     *
     * @param task the task for this action
     * @param board the <code>Board</code> to send
     */
    public void sendBoard(SimTask task, Board board) {
        Messages.sendBoard(task, board, channel, currentUid);
    }

    /**
     * Sends a graphical update of specific spaces to the client.
     *
     * @param task the task for this action
     * @param updates the updates to send
     */
    public void sendUpdate(SimTask task, Collection<BoardSpace> updates) {
        Messages.sendUpdate(task, updates, channel,
                            new UserID [] {currentUid});
    }

    /**
     * Sends the statistics of the given character to the client.
     *
     * @param task the task for this action
     * @param character the character who's statistics will be sent
     */
    public void sendCharacter(SimTask task, PlayerCharacter character) {
        Messages.sendCharacter(task, character.getID(),
                               character.getStatistics(), channel, currentUid);
    }

    /**
     * Sends a server text message (different from a client chat message) to
     * the client.
     *
     * @param task the task for this action
     * @param message the message to send
     */
    public void sendTextMessage(SimTask task, String message) {
        Messages.sendTextMessage(task, message, channel, currentUid);
    }

}

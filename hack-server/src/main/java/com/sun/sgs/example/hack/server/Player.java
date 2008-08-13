/*
 * This work is hereby released into the Public Domain. 
 * To view a copy of the public domain dedication, visit 
 * http://creativecommons.org/licenses/publicdomain/ or send 
 * a letter to Creative Commons, 171 Second Street, Suite 300, 
 * San Francisco, California, 94105, USA.
 */

package com.sun.sgs.example.hack.server;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;

import com.sun.sgs.example.hack.server.level.Level;

import com.sun.sgs.example.hack.share.Board;
import com.sun.sgs.example.hack.share.BoardSpace;
import com.sun.sgs.example.hack.share.CharacterStats;
import com.sun.sgs.example.hack.share.Commands.Command;

import java.io.Serializable;
import java.math.BigInteger;
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
 */
public class Player
    implements ClientSessionListener, ManagedObject, Serializable {

    private static final long serialVersionUID = 1;

    /**
     * The standard namespace prefix for all players.
     */
    public static final String NAME_PREFIX = "player:";

    // notes whether or not the player is current logged in and
    // playing
    private boolean playing;

    // the user's name (login)
    private String name;

    // the uid currently assigned to this player
    private ManagedReference<ClientSession> currentSessionRef;

    // the channel that this player is currently using
    private ManagedReference<Channel> channelRef;

    // the game the user is currently playing, and its message handler
    private ManagedReference<Game> gameRef;

    // the handler that will interpret the messages sent to this
    // player
    private MessageHandler messageHandler = null;

    // this player's character manager
    private ManagedReference<PlayerCharacterManager> characterManagerRef;

    /**
     * Creates a <code>Player</code> instance.
     *
     * @param name the player's name
     */
    private Player(String name) {
        playing = false;
        channelRef = null;
        currentSessionRef = null;
        this.name = name;
        characterManagerRef = AppContext.getDataManager().
            createReference(new PlayerCharacterManager(this));
    }

    /**
     * Returns the current {@code Channel} used to send commands to
     * this player.
     *
     * @return the channel or {@code null} if {@code channelRef} is
     *         {@code null}.
     */
    Channel channel() {
        return channelRef == null ? null : channelRef.get();
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
    public static Player getInstance(String name) {
        DataManager dataManager = AppContext.getDataManager();

        // try to lookup the existing Player
        Player player = null;
        try {
            player = (Player) dataManager.getBinding(NAME_PREFIX + name);
        } catch (NameNotBoundException e) {
            player = new Player(name);
            dataManager.setBinding(NAME_PREFIX + name, player);
        }

        return player;
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
    public PlayerCharacterManager getCharacterManager() {
        return characterManagerRef.get();
    }    

    /**
     * Sets the current {@code Session} for this {@code Player}, which
     * changes from session to session. Typically this is called when
     * the player first logs again, and not again until the player
     * logs out and logs back in.
     *
     * @param session the {@code Session} associated with the player.
     */
    public void setCurrentSession(ClientSession session) {
        DataManager dataMgr = AppContext.getDataManager();
        dataMgr.markForUpdate(this);
        currentSessionRef = dataMgr.createReference(session);
        
        // Also inform the client of the session ID
        // FIXME, this is hacked in as the only non-channel message
        // for ease of porting -JM
        BigInteger sid = currentSessionRef.getId();
        byte[] bytes = sid.toByteArray();
        session.send(ByteBuffer.wrap(bytes));
    }

    /**
     * Returns this <code>Player</code>'s current <code>UserID</code>. Note
     * that this is only meaningful if <code>isPlaying</code> returns
     * true. Otherwise this will return null.
     *
     * @return the current player session, or {@code null} if the player is
     *         not currently playing
     */
    public ClientSession getCurrentSession() {
        return currentSessionRef == null ? null : currentSessionRef.get();
    }

    /**
     * Moves the player into the provided <code>Game</code>. This
     * causes the <code>Player</code> to leave the game they are
     * currently playing (if they are currently playing a game) and
     * notify the new game that they are joining. If the reference
     * provided is null, then this <code>Player</code> is removed from
     * the current game and sets itself as not playing any games.
     *
     * <p>
     *
     * When the <code>Player</code> is first started, it is not
     * playing any games. In practice, this method is only called with
     * a value of null when the associated client logs out of the
     * server.
     *
     * @param game the new <code>Game</code>, or null if the player is
     *             only being removed from a game
     */
    public void moveToGame(Game game) {
        AppContext.getDataManager().markForUpdate(this);

        // if we were previously playing a game, leave it
        if (isPlaying()) {
            gameRef.getForUpdate().leave(this);
            characterManagerRef.getForUpdate().setCurrentLevel(null);
            playing = false;
        }

        // if we got moved into a valid game, then make the migration
        if (game != null) {
            playing = true;

            // keep track of the new game...
            gameRef = AppContext.getDataManager().createReference(game);

            // ...and handle joining the new game
            messageHandler = game.join(this);
        }

        // if we're no longer playing, then our user id is no longer valid
        if (! playing)
            this.currentSessionRef = null;
    }

    /**
     * This is used to handle leaving dungeons before we can actually call
     * the <code>moveToGame</code> (eg, when we die). It handles multiple
     * calls while on the same level cleanly (which is done by paranoid
     * checking when leaving dungeons).
     */
    public void leaveCurrentLevel() {
        PlayerCharacterManager pcm = characterManagerRef.get();
        Level level = pcm.getCurrentLevel();

        if (level != null) {
            level.removeCharacter(pcm);
            pcm.setCurrentLevel(null);
        }
    }
    
    /**
     * Called when the player joins a channel. In this system, the player
     * only joins a new channel in the context of joining a new game, and
     * the player is never on more than one channel.
     *
     * @param newChannel the new channel
     */
    public void userJoinedChannel(Channel newChannel) {
        DataManager dataManager = AppContext.getDataManager();
        dataManager.markForUpdate(this);
        channelRef = dataManager.createReference(newChannel);
    }

    /**
     * Called when data arrives from the a user. In this case, this is called
     * any time the client associated with this <code>Player</code> sends
     * a message directly to the server (i.e., not a broadcast message like a
     * chat comment). This method, therefore, is the handler for all client
     * messages, and so we hold the lock on the <code>Player</code> and not
     * some more generally shared logic while we're processing messages.
     *
     * @param message the data sent to the player
     */
    public void receivedMessage(ByteBuffer message) {
        // call the message handler to interpret the message ... note that the
        // proxy model here means that we're blocking the player, and not the
        // game itself, while we're handling the message
        byte[] messageBytes = new byte[message.remaining()];
        message.get(messageBytes);
        messageHandler.handleMessage(this, messageBytes);
    }

    /**
     * Removes this {@code Player} from the current game.
     *
     * @param graceful whether the diconnection was graceful
     */
    public void disconnected(boolean graceful) {
        moveToGame(null);
    }

    /**
     * Sends a complete <code>Board</code> to the client.
     *
     * @param board the <code>Board</code> to send
     */
    public void sendBoard(Board board) {
        Messages.sendBoard(getCurrentSession(), board);
    }

    /**
     * Sends a graphical update of specific spaces to the client.
     *
     * @param updates the updates to send
     */
    public void broadcastBoardUpdate(Collection<BoardSpace> updates) {
        Messages.broadcastBoardUpdate(channel(), updates);
    }

    /**
     * Notifies the client that they have sent an unhandled command to
     * the specified channel.
     *
     * @param channelName the name of the channel that the unhandled
     * command was sent on
     *
     * @param command the unhandled command
     */
    public void notifyUnhandledCommand(String channelName, Command command) {
	Messages.sendNotificationOfUnhandledCommand(getCurrentSession(),
						    channelName, command);
    }

    /**
     * Sends the statistics of the given character to the client.
     *
     * @param character the character who's statistics will be sent
     */
    public void sendCharacter(PlayerCharacter character) {
        Messages.sendCharacter(getCurrentSession(), character.getID(), 
			       character.getStatistics());                               
    }

    /**
     * Sends a set of character statistics to the player.
     *
     * @param id the character's identifier
     * @param stats the character statistics
     */
    public void sendCharacterStats(int id, CharacterStats stats) {
        Messages.sendCharacter(getCurrentSession(), id, stats);
    }

    /**
     * Sends a server text message (different from a client chat message) to
     * the client.
     *
     * @param message the message to send
     */
    public void sendServerMessage(String message) {
        Messages.sendServerMessage(getCurrentSession(), message);
    }

    public String toString() {
	return name;
    }

}

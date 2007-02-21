package com.sun.sgs.example.battleboard.server;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;

/**
 * Matchmaker is responsible for collecting Players together until
 * there are enough to start a new Game.  <p>
 * 
 * This very basic implementation starts a new game as soon as
 * <code>PLAYERS_PER_GAME</code> players have arrived.  More
 * sophisticated implementations would allow players to specify
 * constraints on the games (for example, if there are other players
 * that prefer to play together, or if players are ranked and want to
 * play against players of roughly equal skill, etc) and the
 * Matchmaker would attempt to satisfy those constraints when placing
 * players into Games.  <p>
 * 
 * Users specify what playerName they wish to use.  In this example,
 * there is no persistant binding between a user and a playerName; a
 * user can use as many different playerNames as he or she wishes, and
 * different users can use the same playerName.  The only restriction
 * is that all of the playerNames joined into a particular game must
 * be unique.
 */
public class Matchmaker
    implements ManagedObject, Serializable
{
    private static final long serialVersionUID = 1;

    private static Logger log =
	    Logger.getLogger(Matchmaker.class.getName());

    private static final String MATCHMAKER_NAME = "matchmaker";

    private Channel channel;

    private int PLAYERS_PER_GAME = 2;

    private Set<ManagedReference> waitingPlayers =
	    new HashSet<ManagedReference>();

    public static Matchmaker getInstance() {
        return AppContext.getDataManager().getBinding(
                MATCHMAKER_NAME, Matchmaker.class);
    }

    /**
     * Only allow construction via the startingUp() static method.
     */
    protected Matchmaker() {
        ChannelManager channelMgr = AppContext.getChannelManager();
        channel =
            channelMgr.createChannel("matchmaker", null, Delivery.RELIABLE);
    }

    /**
     * 
     */
    public static void initialize(Properties props) {
        DataManager dataMgr = AppContext.getDataManager();
        
        try {
            dataMgr.getBinding(
                MATCHMAKER_NAME, Matchmaker.class);
        } catch (NameNotBoundException e) {
            dataMgr.setBinding(MATCHMAKER_NAME, new Matchmaker());
        }
    }

    /**
     * Adds a new user to the channel.
     * 
     * @param session the ClientSession of the new user
     */
    public void addUserID(ClientSession session) {
        channel.join(session, null);
    }

    /**
     * Informs the user that a player with the same player name that
     * they have requested is already waiting to join a game.
     * <p>
     * 
     * @param player the Player to send to
     */
    protected void sendAlreadyJoined(Player player) {
        channel.send(player.getSession(), "already-joined".getBytes());
    }

    /**
     * Dispatches messages from users.
     * <p>
     * 
     * For a simple matchmaker like this one, the only expected message
     * is a request to "join". Messages that do not begin with the
     * prefix <code>"join"</code> are rejected out of hand.
     * 
     * @param player the Player of the user from whom the message is
     * 
     * @param message the contents of the message
     */
    public void receivedMessage(Player player, byte[] message) {

        String text = new String(message);

        String[] tokens = text.split("\\s+");
        if (tokens.length == 0) {
            log.warning("empty message");
            return;
        }

        if (tokens.length != 2) {
            log.warning("bad join (" + text + ")");
            return;
        }

        String command = tokens[0];
        String playerName = tokens[1];

        if (!"join".equals(command)) {
            log.warning("Matchmaker got non-join command: `" + command + "'");
            return;
        }

        log.fine("Matchmaker: join from `" + playerName + "'");

	log.finer("Matchmaker before join has " +
	    waitingPlayers.size() + " waiting");

        /*
         * Before adding this user under the given playerName, check
         * that the playerName is not already in use. If so, then reject
         * the join.
         */
        for (ManagedReference ref : waitingPlayers) {
            Player waitingPlayer = ref.get(Player.class);
            if (playerName.equals(waitingPlayer.getPlayerName())) {
                log.warning("Matchmaker already has `" + playerName);
                sendAlreadyJoined(player);
                return;
            }
        }

        DataManager dataMgr = AppContext.getDataManager();
        dataMgr.markForUpdate(player);

        player.setPlayerName(playerName);
        waitingPlayers.add(dataMgr.createReference(player));

        /*
         * If there are enough players waiting, create a game.
         * 
         * Another technique would be to queue a new task to check
         * whether we have enough players to create a game and/or
         * partition the set of players into games. This has the
         * possible advantage of releasing the lock on this player,
         * which we otherwise continue to hold.
         */
	log.finer("Matchmaker after join has " +
	    waitingPlayers.size() + " waiting");

        if (waitingPlayers.size() == PLAYERS_PER_GAME) {
	    if (log.isLoggable(Level.FINEST)) {
		log.finest("Creating a new game for:");
		for (ManagedReference ref : waitingPlayers) {
		    Player waitingPlayer = ref.get(Player.class);
		    log.finest("    " + waitingPlayer.getPlayerName());
		}
	    }

            Game.create(waitingPlayers);
            waitingPlayers.clear();
        }
    }

    public void playerDisconnected(Player player) {
        DataManager dataMgr = AppContext.getDataManager();
        waitingPlayers.remove(dataMgr.createReference(player));
    }
}

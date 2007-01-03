package com.sun.sgs.example.battleboard.server;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.example.battleboard.BattleBoard;

import static com.sun.sgs.example.battleboard.BattleBoard.PositionValue.CITY;

/**
 * Game encapuslates the server-side management of a BattleBoard game.
 * <p>
 * Once created with a set of players, a Game will create a new
 * communication channel and begin the BattleBoard protocol with the
 * players. Once the game has started, the Game reacts to player
 * messages and coordinates the turns. When the game ends, the Game
 * records wins and losses, removes all players from this game's channel
 * and closes the server-side resources.
 */
public class Game implements ManagedObject, Serializable {

    private static final long serialVersionUID = 1;

    private static Logger log =
	Logger.getLogger(Game.class.getName());

    private final String gameName;
    private final Channel channel;

    /*
     * Players who are attached to the game and are still engaged
     * (they have cities, and have not withdrawn).
     */
    private final LinkedList<ManagedReference> players;

    /*
     * Players who have quit or lost the game may linger as
     * spectators, if they wish.
     */
    private final LinkedList<ManagedReference> spectators;

    /*
     * The current board of each player.
     */
    private final Map<String, ManagedReference> playerBoards;

    /*
     * A mapping from the player name to the GLOReference for the
     * GLO that contains their "history" (how many games they've
     * won/lost).
     */
    private final Map<String, ManagedReference> nameToHistory;

    /*
     * The currentPlayer is the player currently making a move.
     */

    private ManagedReference currentPlayerRef;

    /*
     * The number of users currently attached to the game.  This is
     * used to decide when there are enough players to actually begin
     * play.
     */
    private int userCount;

    /*
     * The default BattleBoard game is defined in the {@link
     * BattleBoard} class.
     * 
     * For the sake of simplicity, this implementation does not
     * include any way of specifying a different number of players
     * and/or different board sizes.  These would not be hard to
     * change; just change the call to createBoard to create a board
     * of the desired size and number of cities.
     */
    private final int boardWidth = BattleBoard.DEFAULT_BOARD_WIDTH;
    private final int boardHeight = BattleBoard.DEFAULT_BOARD_WIDTH;
    private final int numCities = BattleBoard.DEFAULT_NUM_CITIES;

    /**
     * Creates a new BattleBoard game object for a given set of
     * players.
     * 
     * @param newPlayers a set of GLOReferences to Player GLOs
     */
    protected Game(Set<ManagedReference> newPlayers) {

	/*
	 * Error checking:  without players, we can't proceed.  Note
	 * that this impl permits a single player game, which is
	 * permitted by the spec (but isn't usually very much fun to
	 * play).
	 */ 
        if (newPlayers == null) {
            throw new NullPointerException("newPlayers is null");
        }
        if (newPlayers.size() == 0) {
            throw new IllegalArgumentException("newPlayers is empty");
        }
        
        DataManager dataMgr = AppContext.getDataManager();
        ManagedReference gameRef = dataMgr.createReference(this);

        gameName = "GameChannel-" + gameRef.getId();

        log.info("New game channel is `" + gameName + "'");

	/*
	 * Create the list of players from the set of players, and
	 * shuffle the order of the list to get the turn order.
	 */
        players = new LinkedList<ManagedReference>(newPlayers);
        Collections.shuffle(players);

	/*
	 * Create the spectators list (initially empty).
	 */ 
        spectators = new LinkedList<ManagedReference>();

	/*
	 * Create the map between player names and their boards, and
	 * then populate it with freshly-created boards for each
	 * player.
	 *
	 * Note that to keep this implementation simple, the server
	 * chooses the board of each player:  the player has no
	 * control over where his or her cities are placed.
	 */
        playerBoards = new HashMap<String, ManagedReference>();
        for (ManagedReference playerRef : players) {
            Player player = playerRef.get(Player.class);
            playerBoards.put(player.getPlayerName(),
		    createBoard(player.getPlayerName()));
        }

	/*
	 * Create a map from player name to GLOReferences to the GLOs
	 * that store the history of each player to cache this info.
	 */
        nameToHistory = new HashMap<String, ManagedReference>();

        ChannelManager channelMgr = AppContext.getChannelManager();
        channel = channelMgr.createChannel(gameName, null, Delivery.RELIABLE);

	userCount = 0;
    }

    /**
     * Creates a new Game object for the given players.
     * 
     * @param players the set of GLOReferences to players
     * 
     * @return the GLOReference for a new Game
     */
    public static ManagedReference create(Set<ManagedReference> players) {
	ManagedReference gameRef = task.createGLO(new Game(players));
	Channel channel = gameRef.get(task).channel;

	/*
	 * Join all of the players onto this game's channel.
	 */
        for (ManagedReference playerRef : players) {
            Player player = playerRef.get(task);
            player.gameStarted(gameRef);
            task.join(player.getUID(), channel);
        }

	return gameRef;
    }

    /**
     * Creates a board for the given playerName.  <p>
     *
     * The city locations are chosen randomly.
     *
     * @param playerName the name of the player
     *
     * @return a {@link GLOReference} for the new {@link Board}.
     */
    protected ManagedReference createBoard(String playerName) {
        SimTask task = SimTask.getCurrent();

        Board board = new Board(playerName, boardWidth, boardHeight, numCities);
        board.populate();

        ManagedReference ref = task.createGLO(board, gameName +
		"-board-" + playerName);

        log.finer("createBoard[" + playerName + "] returning " + ref);
        return ref;
    }

    /**
     * Sends the "ok" message, which indicates to a player that he or
     * she has been chosen to join a game, to all of the players.
     */
    protected void sendJoinOK() {
        SimTask task = SimTask.getCurrent();
        for (ManagedReference ref : players) {
            Player player = ref.peek(task);
            sendJoinOK(player);
        }
    }

    /**
     * Sends the "ok" message to a particular player. <p>
     *
     * This message includes the list of city locations for the player,
     * in order to display them on his/her screen.
     *
     * @param player the player to whom to send the message
     */
    protected void sendJoinOK(Player player) {
        SimTask task = SimTask.getCurrent();

        StringBuffer buf = new StringBuffer("ok ");

        log.finer("playerBoards size " + playerBoards.size());

        GLOReference boardRef = playerBoards.get(player.getPlayerName());
        Board board = (Board) boardRef.peek(task);

        buf.append(board.getWidth() + " ");
        buf.append(board.getHeight() + " ");
        buf.append(board.getStartCities());

        for (int i = 0; i < board.getWidth(); ++i) {
            for (int j = 0; j < board.getHeight(); ++j) {
                if (board.getBoardPosition(i, j) == CITY) {
                    buf.append(" " + i + " " + j);
                }
            }
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(buf.toString().getBytes());
        byteBuffer.position(byteBuffer.limit());

        task.sendData(channel, new UserID[] { player.getUID() },
                byteBuffer.asReadOnlyBuffer(), true);
    }

    /**
     * Broadcasts the turn order to all of the players.
     */
    protected void sendTurnOrder() {
        SimTask task = SimTask.getCurrent();
        StringBuffer buf = new StringBuffer("turn-order ");

        for (ManagedReference playerRef : players) {
            Player player = playerRef.peek(task);
            buf.append(" " + player.getPlayerName());
        }

        broadcast(buf);
    }

    /**
     * Starts the next move.
     */
    protected void startNextMove() {
        SimTask task = SimTask.getCurrent();
        log.finest("Running Game.startNextMove");

        currentPlayerRef = players.removeFirst();
        players.addLast(currentPlayerRef);
        Player player = currentPlayerRef.peek(task);
        sendMoveStarted(player);
    }

    /**
     * Informs all of the players that it is the turn of the given
     * player.
     *
     * @param player the player whose move is starting
     */
    protected void sendMoveStarted(Player player) {
        StringBuffer buf = new StringBuffer("move-started " +
		player.getPlayerName());
        broadcast(buf);
    }

    /**
     * Permits the given player to pass.
     *
     * @param player the player who passes
     */
    protected void handlePass(Player player) {
        StringBuffer buf = new StringBuffer("move-ended ");
        buf.append(player.getPlayerName());
        buf.append(" pass");

        broadcast(buf);
        startNextMove();
    }

    /**
     * Handles the logic of one move.
     *
     * @param player the player whose turn it is
     *
     * @param tokens the tokens of the command
     */
    protected void handleMove(Player player, String[] tokens) {
        SimTask task = SimTask.getCurrent();

        String bombedPlayerNick = tokens[1];

        ManagedReference boardRef = playerBoards.get(bombedPlayerNick);
        if (boardRef == null) {
            log.warning(player.getPlayerName() +
		    " tried to bomb non-existant player " + bombedPlayerNick);
            handlePass(player);
            return;
        }
        Board board = boardRef.get(task);

        int x = Integer.parseInt(tokens[2]);
        int y = Integer.parseInt(tokens[3]);

        /*
         * Check that x and y are in bounds. If not, treat it as a pass.
         */

        if ((x < 0) || (x >= board.getWidth()) || (y < 0)
                || (y >= board.getHeight())) {
            log.warning(player.getPlayerName() +
		    " tried to move outside the board");
            handlePass(player);
            return;
        }

        Board.PositionValue result = board.bombBoardPosition(x, y);

        String outcome = "";
        switch (result) {
            case HIT:
                outcome = board.lost() ? "LOSS" : "HIT";
                break;
            case NEAR:
                outcome = "NEAR_MISS";
                break;
            case MISS:
                outcome = "MISS";
                break;
            default:
                log.severe("Unhandled result in handleMove: " + result.name());
                outcome = "MISS";
                break;
        }

        StringBuffer buf = new StringBuffer("move-ended ");
        buf.append(player.getPlayerName());
        buf.append(" bomb");
        buf.append(" " + bombedPlayerNick);
        buf.append(" " + x);
        buf.append(" " + y);
        buf.append(" " + outcome);

        broadcast(buf);

        // If the bombed player has lost, do extra processing
        if (board.lost()) {
            handlePlayerLoss(bombedPlayerNick);
        }

        /*
         * Check whether some player has won. Under ordinary
         * circumstances, a player wins by making a move that destroys
         * the last city of his or her last opponent, but it is also
         * possible for a player to drop a bomb on his or her own board,
         * destroying their last city, and thereby forfeiting the game
         * to his or her opponent. Therefore we need to not only check
         * whether someone won, but who.
         */

        if (players.size() <= 1) {

            /*
             * It shouldn't be possible for the boardset to be empty at
             * this point, but just in case, check for the expected
             * case.
             */
            if (players.size() == 1) {
                ManagedReference playerRef = players.get(0);
                Player winner = playerRef.peek(task);
                ManagedReference historyRef =
			nameToHistory.get(winner.getUserName());
                PlayerHistory history = historyRef.get(task);
                history.win();
                log.finer(winner.getUserName() + " summary: " +
			history.toString());
            }

            // Someone won, so don't start the next move
            return;
        }

        startNextMove();
    }

    /**
     * Handles the situation when a player loses.
     *
     * @param loserNick the nickname of the losing player
     */
    protected void handlePlayerLoss(String loserNick) {
        SimTask task = SimTask.getCurrent();

        playerBoards.remove(loserNick);

        Iterator<ManagedReference> i = players.iterator();
        Player loser = null;
        while (i.hasNext()) {
            ManagedReference ref = i.next();
            Player player = ref.peek(task);
            if (loserNick.equals(player.getPlayerName())) {
                loser = player;
                spectators.add(ref);
                i.remove();
            }
        }

        if (loser == null) {
            log.severe("Can't find losing Player nicknamed `" +
		    loserNick + "'");
            return;
        }

        ManagedReference historyRef =
		nameToHistory.get(loser.getUserName());

        PlayerHistory history = historyRef.get(task);
        history.lose();

        log.fine(loserNick + " summary: " + history.toString());
    }

    /**
     * Handles the response from a move.
     *
     * @param playerRef a GLOReference for the player
     *
     * @param tokens the components of the response
     */
    protected void handleResponse(ManagedReference playerRef,
            String[] tokens) {

        if (!playerRef.equals(currentPlayerRef)) {
            log.severe("PlayerRef != CurrentPlayerRef");
            return;
        }

        SimTask task = SimTask.getCurrent();
        Player player = playerRef.peek(task);
        String cmd = tokens[0];

        if ("pass".equals(cmd)) {
            handlePass(player);
        } else if ("move".equals(cmd)) {
            handleMove(player, tokens);
        } else {
            log.warning("Unknown command `" + cmd + "'");
            handlePass(player);
        }
    }

    // Class-specific utility methods.

    /**
     * Broadcasts a given message to all of the players.
     *
     * @param buf the message to broadcast
     */
    private void broadcast(StringBuffer buf) {
        SimTask task = SimTask.getCurrent();
        UserID[] uids = new UserID[players.size() + spectators.size()];

        int i = 0;
        for (ManagedReference ref : players) {
            Player player = ref.peek(task);
            uids[i++] = player.getUID();
        }

        for (ManagedReference ref : spectators) {
            Player player = ref.peek(task);
            uids[i++] = player.getUID();
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(buf.toString().getBytes());
        byteBuffer.position(byteBuffer.limit());

        log.finest("Game: Broadcasting " + byteBuffer.position() +
		" bytes on " + channel);

        task.sendData(channel, uids, byteBuffer.asReadOnlyBuffer(), true);
    }

    /**
     * Adds a new PlayerHistory GLOReference to the set of histories
     * associated with this game.
     * 
     * When the game is done, each player is updated with a win or loss.
     * 
     * @param playerName the name of the player
     * 
     * @param historyRef a GLOReference to the PlayerHistory instance
     * for the player with the given name
     */
    public void addHistory(PlayerHistory history) {
        nameToHistory.put(history.getPlayerName(), history);
    }

    /**
     * Handle data that was sent directly to the server.
     *
     * The Player GLO's userDataReceived handler forwards these events
     * to us since we want to collect them across the entire channel.
     *
     * @param uid the UserID of the sender
     *
     * @param data the buffer of data received
     */
    public void receivedMessage(Player player, byte[] message) {
        log.finest("Game: Direct data from " + player);

        String text = new String(message);

        log.finest("userDataReceived: (" + text + ")");
        String[] tokens = text.split("\\s+");
        if (tokens.length == 0) {
            log.warning("empty message");
            return;
        }

        ManagedReference playerRef = Player.getRef(uid);

        if (playerRef == null) {
            log.warning("No Player found for uid " + uid);
        }

        handleResponse(playerRef, tokens);
    }

    // Channel Join/Leave methods

    /**
     * Waits until we get joinedChannel from all our players before
     * starting the game.
     *
     * The Player GLO's userJoined handler forwards these events to
     * us since we want to collect them across the entire channel.
     *
     * @param cid the ChannelID
     *
     * @param uid the UserID of the joiner
     */
    public void joinedChannel(Channel cid, UserID uid) {
        log.finer("Game: User " + uid + " joined channel " + cid);

	userCount++;

	if (userCount < players.size()) {
	    return;
	}

        log.finer("Everyone's on the channel, start the game");

        SimTask task = SimTask.getCurrent();

        if (log.isLoggable(Level.FINE)) {
            log.finest("playerBoards size " + playerBoards.size());
            for (Map.Entry<String, ManagedReference> x :
			playerBoards.entrySet())
	    {
                log.finest("playerBoard[" + x.getKey() + "]=`" +
			x.getValue() + "'");
            }
        }

        sendJoinOK();
        sendTurnOrder();
        startNextMove();
    }

    /**
     * Waits until we get leftChannel from all our players before
     * deleting the game.
     *
     * The Player GLO's userLeft handler forwards these events to
     * us since we want to collect them across the entire channel.
     *
     * @param cid the ChannelID
     *
     * @param uid the UserID of the departing user
     */
    public void playerDisconnected(Player player) {
        log.finer("Game: User " + uid + " left channel " + cid);

        SimTask task = SimTask.getCurrent();

        ManagedReference playerRef = Player.getRef(uid);

        if (playerRef == null) {
            log.warning("No PlayerRef found for uid " + uid);
        }

        // Tell the player this game is over
	Player player = playerRef.get(task);
        if (player == null) {
            log.warning("No Player found for uid " + uid);
        }

	log.finer("Game end for for " +
	    player.getPlayerName() + " (" +
	    player.getUserName() + ") uid " + uid);

	player.gameEnded(task.lookupReferenceFor(this));

	players.remove(playerRef);
	spectators.remove(playerRef);
	playerBoards.remove(player.getPlayerName());

	if (log.isLoggable(Level.FINEST)) {
	    log.finest(players.size() + " players, " +
		    spectators.size() + " spectators, " +
		    playerBoards.size() + " boards");
	}

	if (players.isEmpty() && spectators.isEmpty()) {

	    // The last player left, so destroy this Game
	    log.finer("Destroying game");

	    // Destroy all the players' boards
	    for (ManagedReference ref : playerBoards.values()) {
		task.destroyGLO(ref);
	    }

	    // Destroy this Game GLO
	    task.destroyGLO(task.lookupReferenceFor(this));
	}
    }
}

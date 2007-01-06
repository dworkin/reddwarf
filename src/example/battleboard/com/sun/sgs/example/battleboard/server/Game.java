package com.sun.sgs.example.battleboard.server;

import java.io.Serializable;
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
        dataMgr.setBinding(gameName, this);

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
            Board board = createBoard(player.getPlayerName());
            playerBoards.put(player.getPlayerName(),
        	    dataMgr.createReference(board));
        }

	/*
	 * Create a map from player name to GLOReferences to the GLOs
	 * that store the history of each player to cache this info.
	 */
        nameToHistory = new HashMap<String, ManagedReference>();

        ChannelManager channelMgr = AppContext.getChannelManager();
        channel = channelMgr.createChannel(gameName, null, Delivery.RELIABLE);
    }

    /**
     * Creates a new Game object for the given players.
     * 
     * @param players the set of ManagedReferences to players
     * 
     * @return the new Game
     */
    public static Game create(Set<ManagedReference> players) {
	Game game = new Game(players);

	// Join all of the players onto this game's channel.
        for (ManagedReference playerRef : players) {
            Player player = playerRef.get(Player.class);
            player.gameStarted(game);
            game.channel.join(player.getSession(), null);
            game.sendJoinOK(player);
        }

        if (log.isLoggable(Level.FINEST)) {
            log.finest("playerBoards size " + game.playerBoards.size());
            for (Map.Entry<String, ManagedReference> x :
                    game.playerBoards.entrySet())
	    {
                log.finest("playerBoard[" + x.getKey() + "]=`" +
			x.getValue() + "'");
            }
        }
        
        game.sendTurnOrder();
        game.startNextMove();

	return game;
    }
    
    private String boardNameForPlayer(String playerName) {
        return gameName + "-board-" + playerName;
    }

    /**
     * Creates a board for the given playerName.
     * <p>
     * The city locations are chosen randomly.
     *
     * @param playerName the name of the player
     *
     * @return a new {@link Board} for the Player.
     */
    protected Board createBoard(String playerName) {
        Board board = new Board(playerName, boardWidth, boardHeight, numCities);
        board.populate();

        String boardName = boardNameForPlayer(playerName);
        
        DataManager dataMgr = AppContext.getDataManager();
        dataMgr.setBinding(boardName, board);

        log.finer("createBoard[" + playerName + "] returning " + board);
        return board;
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

        StringBuffer buf = new StringBuffer("ok ");

        log.finer("playerBoards size " + playerBoards.size());

        ManagedReference boardRef = playerBoards.get(player.getPlayerName());
        Board board = boardRef.get(Board.class);

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

        channel.send(player.getSession(), buf.toString().getBytes());
    }

    /**
     * Broadcasts the turn order to all of the players.
     */
    protected void sendTurnOrder() {
        StringBuffer buf = new StringBuffer("turn-order ");

        for (ManagedReference playerRef : players) {
            Player player = playerRef.get(Player.class);
            buf.append(" " + player.getPlayerName());
        }

        broadcast(buf);
    }

    /**
     * Starts the next move.
     */
    protected void startNextMove() {
        log.finest("Running Game.startNextMove");

        currentPlayerRef = players.removeFirst();
        players.addLast(currentPlayerRef);
        Player player = currentPlayerRef.get(Player.class);
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
	
        String bombedPlayerNick = tokens[1];

        ManagedReference boardRef = playerBoards.get(bombedPlayerNick);
        if (boardRef == null) {
            log.warning(player.getPlayerName() +
		    " tried to bomb non-existant player " + bombedPlayerNick);
            handlePass(player);
            return;
        }
        
        Board board = boardRef.get(Board.class);

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
                Player winner = playerRef.get(Player.class);
                ManagedReference historyRef =
			nameToHistory.get(winner.getPlayerName());
                PlayerHistory history = historyRef.get(PlayerHistory.class);
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

        playerBoards.remove(loserNick);

        Iterator<ManagedReference> i = players.iterator();
        Player loser = null;
        while (i.hasNext()) {
            ManagedReference ref = i.next();
            Player player = ref.get(Player.class);
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
		nameToHistory.get(loser.getPlayerName());

        PlayerHistory history = historyRef.get(PlayerHistory.class);
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
    protected void handleResponse(Player player, String[] tokens) {

        if (!player.equals(currentPlayerRef.get(Player.class))) {
            log.severe("Player != CurrentPlayer");
            return;
        }

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

        byte[] message = buf.toString().getBytes();

        log.finest("Game: Broadcasting " + message.length +
		" bytes on " + channel);

        channel.send(message);
    }

    /**
     * Adds a new PlayerHistory GLOReference to the set of histories
     * associated with this game.
     * 
     * When the game is done, each player is updated with a win or loss.
     * 
     * @param history the PlayerHistory instance for the player
     */
    public void addHistory(PlayerHistory history) {
        nameToHistory.put(history.getPlayerName(),
        	AppContext.getDataManager().createReference(history));
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

        log.finest("receivedMessage: (" + text + ")");
        String[] tokens = text.split("\\s+");
        if (tokens.length == 0) {
            log.warning("empty message");
            return;
        }

        handleResponse(player, tokens);
    }

    /**
     * Waits until all our players leave before
     * deleting the game.
     */
    public void playerDisconnected(Player player) {
        log.finer("Game: player " + player + " left " + this);

	player.gameEnded(this);
        
        DataManager dataMgr = AppContext.getDataManager();
        ManagedReference playerRef = dataMgr.createReference(player);

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
                Board board = ref.get(Board.class);
                String boardName = boardNameForPlayer(player.getPlayerName());
                dataMgr.removeBinding(boardName);
                dataMgr.removeObject(board);
	    }

	    // Destroy this Game
            dataMgr.removeBinding(this.gameName);
            dataMgr.removeObject(this);
	}
    }
}

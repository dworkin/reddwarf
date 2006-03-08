/*
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, U.S.A. All rights reserved.
 * 
 * Sun Microsystems, Inc. has intellectual property rights relating to
 * technology embodied in the product that is described in this
 * document. In particular, and without limitation, these intellectual
 * property rights may include one or more of the U.S. patents listed at
 * http://www.sun.com/patents and one or more additional patents or
 * pending patent applications in the U.S. and in other countries.
 * 
 * U.S. Government Rights - Commercial software. Government users are
 * subject to the Sun Microsystems, Inc. standard license agreement and
 * applicable provisions of the FAR and its supplements.
 * 
 * Use is subject to license terms.
 * 
 * This distribution may include materials developed by third parties.
 * 
 * Sun, Sun Microsystems, the Sun logo and Java are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other
 * countries.
 * 
 * This product is covered and controlled by U.S. Export Control laws
 * and may be subject to the export or import laws in other countries.
 * Nuclear, missile, chemical biological weapons or nuclear maritime end
 * uses or end users, whether direct or indirect, are strictly
 * prohibited. Export or reexport to countries subject to U.S. embargo
 * or to entities identified on U.S. export exclusion lists, including,
 * but not limited to, the denied persons and specially designated
 * nationals lists is strictly prohibited.
 * 
 * Copyright © 2006 Sun Microsystems, Inc., 4150 Network Circle, Santa
 * Clara, California 95054, Etats-Unis. Tous droits réservés.
 * 
 * Sun Microsystems, Inc. détient les droits de propriété intellectuels
 * relatifs à la technologie incorporée dans le produit qui est décrit
 * dans ce document. En particulier, et ce sans limitation, ces droits
 * de propriété intellectuelle peuvent inclure un ou plus des brevets
 * américains listés à l'adresse http://www.sun.com/patents et un ou les
 * brevets supplémentaires ou les applications de brevet en attente aux
 * Etats - Unis et dans les autres pays.
 * 
 * L'utilisation est soumise aux termes de la Licence.
 * 
 * Cette distribution peut comprendre des composants développés par des
 * tierces parties.
 * 
 * Sun, Sun Microsystems, le logo Sun et Java sont des marques de
 * fabrique ou des marques déposées de Sun Microsystems, Inc. aux
 * Etats-Unis et dans d'autres pays.
 * 
 * Ce produit est soumis à la législation américaine en matière de
 * contrôle des exportations et peut être soumis à la règlementation en
 * vigueur dans d'autres pays dans le domaine des exportations et
 * importations. Les utilisations, ou utilisateurs finaux, pour des
 * armes nucléaires,des missiles, des armes biologiques et chimiques ou
 * du nucléaire maritime, directement ou indirectement, sont strictement
 * interdites. Les exportations ou réexportations vers les pays sous
 * embargo américain, ou vers des entités figurant sur les listes
 * d'exclusion d'exportation américaines, y compris, mais de manière non
 * exhaustive, la liste de personnes qui font objet d'un ordre de ne pas
 * participer, d'une façon directe ou indirecte, aux exportations des
 * produits ou des services qui sont régis par la législation américaine
 * en matière de contrôle des exportations et la liste de ressortissants
 * spécifiquement désignés, sont rigoureusement interdites.
 */

package com.sun.gi.apps.battleboard.server;

import static com.sun.gi.apps.battleboard.BattleBoard.PositionValue.CITY;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.gi.apps.battleboard.BattleBoard;
import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.gloutils.SequenceGLO;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;

/**
 * Game encapuslates the server-side management of a BattleBoard game.
 * Once created with a set of players, a Game will create a new
 * communication channel and begin the BattleBoard protocol with the
 * players. Once the game has started, the Game reacts to player
 * messages and coordinates the turns. When the game ends, the Game
 * records wins and losses, removes all players from this game's channel
 * and closes the server-side resources.
 */
public class Game implements GLO {

    private static final long serialVersionUID = 1;

    private static Logger log = Logger.getLogger("com.sun.gi.apps.battleboard.server");

    private String gameName;
    private ChannelID channel;
    private GLOReference<Game> thisRef;
    private LinkedList<GLOReference<Player>> players;
    private LinkedList<GLOReference<Player>> spectators;
    private Map<String, GLOReference<Board>> playerBoards;
    private GLOReference<Player> currentPlayerRef;
    private Map<String, GLOReference<PlayerHistory>> nameToHistory;

    /*
     * The default BattleBoard game is defined in the {@link
     * BattleBoard} class.
     * 
     * For the sake of simplicity, this implementation does not support
     * different numbers of players and/or different board sizes. These
     * would not be hard to change; just change the call to createBoard
     * to create a board of the desired size and number of cities.
     */

    private int boardWidth = BattleBoard.DEFAULT_BOARD_WIDTH;
    private int boardHeight = BattleBoard.DEFAULT_BOARD_WIDTH;
    private int numCities = BattleBoard.DEFAULT_NUM_CITIES;

    /**
     * Creates a new BattleBoard game object for a set of players.
     * 
     * @param newPlayers a set of GLOReferences to Player GLOs
     */
    protected Game(Set<GLOReference<Player>> newPlayers) {

        if (newPlayers == null) {
            throw new NullPointerException("newPlayers is null");
        }
        if (newPlayers.size() == 0) {
            throw new IllegalArgumentException("newPlayers is empty");
        }

        SimTask task = SimTask.getCurrent();

        gameName = "GameChannel-" +
		SequenceGLO.getNext(task, "GameChannelSequence");

        log.info("New game channel is `" + gameName + "'");

        players = new LinkedList<GLOReference<Player>>(newPlayers);
        Collections.shuffle(players);

        spectators = new LinkedList<GLOReference<Player>>();

        playerBoards = new HashMap<String, GLOReference<Board>>();
        for (GLOReference<Player> playerRef : players) {
            Player p = playerRef.get(task);
            playerBoards.put(p.getPlayerName(), createBoard(p.getPlayerName()));
        }

        nameToHistory = new HashMap<String, GLOReference<PlayerHistory>>();
        channel = task.openChannel(gameName);
        task.lock(channel, true);
    }

    /**
     * Creates a new Game object for the given players.
     * 
     * @param players the set of GLOReferences to players
     * 
     * @return the GLOReference for a new Game
     */
    public static GLOReference<Game> create(Set<GLOReference<Player>> players) {
        SimTask task = SimTask.getCurrent();
        GLOReference<Game> ref = task.createGLO(new Game(players));

        ref.get(task).boot(ref);
        return ref;
    }

    protected void boot(GLOReference<Game> ref) {
        SimTask task = SimTask.getCurrent();

        thisRef = ref;

        if (log.isLoggable(Level.FINE)) {
            log.finest("playerBoards size " + playerBoards.size());
            for (Map.Entry<String, GLOReference<Board>> x :
			playerBoards.entrySet())
	    {
                log.finest("playerBoard[" + x.getKey() + "]=`" +
			x.getValue() + "'");
            }
        }

        for (GLOReference<Player> playerRef : players) {
            Player p = playerRef.get(task);
            p.gameStarted(thisRef);
        }

        sendJoinOK();
        sendTurnOrder();
        startNextMove();
    }

    protected GLOReference<Board> createBoard(String playerName) {
        SimTask task = SimTask.getCurrent();

        Board board = new Board(playerName, boardWidth, boardHeight, numCities);
        board.populate();

        GLOReference<Board> ref = task.createGLO(board, gameName +
		"-board-" + playerName);

        log.finer("createBoard[" + playerName + "] returning " + ref);
        return ref;
    }

    public void endGame() {
        SimTask task = SimTask.getCurrent();
        log.finer("Ending Game");
        // Tell all the players this game is over
        for (GLOReference<Player> ref : players) {
            Player p = ref.get(task);
            p.gameEnded(thisRef);
        }

        // Close this game's channel
        task.closeChannel(channel);
        channel = null;

        // Destroy all the players' boards
        for (GLOReference ref : playerBoards.values()) {
            ref.delete(task);
        }

        // null the lists and maps so they can be GC'd
        players.clear();
        spectators.clear();
        playerBoards.clear();

        // Destroy this Game GLO
	thisRef.delete(task);
    }

    protected void sendJoinOK() {
        SimTask task = SimTask.getCurrent();
        for (GLOReference<Player> ref : players) {
            Player p = ref.peek(task);
            task.join(p.getUID(), channel);
            sendJoinOK(p);
        }
    }

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

    protected void sendTurnOrder() {
        SimTask task = SimTask.getCurrent();
        StringBuffer buf = new StringBuffer("turn-order ");

        for (GLOReference<Player> playerRef : players) {
            Player p = playerRef.peek(task);
            buf.append(" " + p.getPlayerName());
        }

        broadcast(buf);
    }

    protected void broadcast(StringBuffer buf) {
        SimTask task = SimTask.getCurrent();
        UserID[] uids = new UserID[players.size() + spectators.size()];

        int i = 0;
        for (GLOReference<Player> ref : players) {
            Player p = ref.peek(task);
            uids[i++] = p.getUID();
        }

        for (GLOReference<Player> ref : spectators) {
            Player p = ref.peek(task);
            uids[i++] = p.getUID();
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(buf.toString().getBytes());
        byteBuffer.position(byteBuffer.limit());

        log.finest("Game: Broadcasting " + byteBuffer.position() +
		" bytes on " + channel);

        task.sendData(channel, uids, byteBuffer.asReadOnlyBuffer(), true);
    }

    protected void sendMoveStarted(Player player) {
        StringBuffer buf = new StringBuffer("move-started " +
		player.getPlayerName());
        broadcast(buf);
    }

    protected void startNextMove() {
        SimTask task = SimTask.getCurrent();
        log.finest("Running Game.startNextMove");

        currentPlayerRef = players.removeFirst();
        players.addLast(currentPlayerRef);
        Player player = currentPlayerRef.peek(task);
        sendMoveStarted(player);
    }

    protected void handlePass(Player player) {
        StringBuffer buf = new StringBuffer("move-ended ");
        buf.append(player.getPlayerName());
        buf.append(" pass");

        broadcast(buf);
        startNextMove();
    }

    protected void handleMove(Player player, String[] tokens) {
        SimTask task = SimTask.getCurrent();

        String bombedPlayerNick = tokens[1];

        GLOReference<Board> boardRef = playerBoards.get(bombedPlayerNick);
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
                GLOReference<Player> playerRef = players.get(0);
                Player winner = playerRef.peek(task);
                GLOReference<PlayerHistory> historyRef =
			nameToHistory.get(winner.getUserName());
                PlayerHistory history = historyRef.get(task);
                history.win();
                log.finer(winner.getUserName() + " summary: " +
			history.toString());
            }

            // queue a new task to handle end of game
            try {
                task.queueTask(thisRef, Game.class.getMethod("endGame"),
                        new Object[] {});
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Someone won, so don't start the next move
            return;
        }

        startNextMove();
    }

    protected void handlePlayerLoss(String loserNick) {
        SimTask task = SimTask.getCurrent();

        playerBoards.remove(loserNick);

        Iterator<GLOReference<Player>> i = players.iterator();
        Player loser = null;
        while (i.hasNext()) {
            GLOReference<Player> ref = i.next();
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

        GLOReference<PlayerHistory> historyRef =
		nameToHistory.get(loser.getUserName());

        PlayerHistory history = historyRef.get(task);
        history.lose();

        log.fine(loserNick + " summary: " + history.toString());
    }

    protected void handleResponse(GLOReference<Player> playerRef,
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

    // Class-specific utilities.

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
    public void addHistory(String playerName,
            GLOReference<PlayerHistory> historyRef) {
        nameToHistory.put(playerName, historyRef);
    }

    /**
     * Handle data that was sent directly to the server.
     */
    public void userDataReceived(UserID uid, ByteBuffer data) {
        log.finest("Game: Direct data from user " + uid);

        byte[] bytes = new byte[data.remaining()];
        data.get(bytes);
        String text = new String(bytes);

        log.finest("userDataReceived: (" + text + ")");
        String[] tokens = text.split("\\s+");
        if (tokens.length == 0) {
            log.warning("empty message");
            return;
        }

        GLOReference<Player> playerRef = Player.getRef(uid);

        if (playerRef == null) {
            log.warning("No Player found for uid " + uid);
        }

        handleResponse(playerRef, tokens);
    }

    // Channel Join/Leave methods

    /**
     * XXX: We should wait until we get joinedChannel from all our
     * players before starting the game.
     */
    public void joinedChannel(ChannelID cid, UserID uid) {
        log.finer("Game: User " + uid + " joined channel " + cid);
    }

    /**
     * XXX: When a player leaves unexpectedly, we should treat it as a
     * "withdraw" command, when we implement withdraw.
     */
    public void leftChannel(ChannelID cid, UserID uid) {
        log.finer("Game: User " + uid + " left channel " + cid);
    }
}

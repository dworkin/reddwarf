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

    private static Logger log =
	Logger.getLogger("com.sun.gi.apps.battleboard.server");

    private final String gameName;
    private final ChannelID channel;
    private final LinkedList<GLOReference<Player>> players;
    private final LinkedList<GLOReference<Player>> spectators;
    private final Map<String, GLOReference<Board>> playerBoards;
    private final Map<String, GLOReference<PlayerHistory>> nameToHistory;

    private GLOReference<Player> currentPlayerRef;
    private int userCount;

    /*
     * The default BattleBoard game is defined in the {@link
     * BattleBoard} class.
     * 
     * For the sake of simplicity, this implementation does not support
     * different numbers of players and/or different board sizes. These
     * would not be hard to change; just change the call to createBoard
     * to create a board of the desired size and number of cities.
     */

    private final int boardWidth = BattleBoard.DEFAULT_BOARD_WIDTH;
    private final int boardHeight = BattleBoard.DEFAULT_BOARD_WIDTH;
    private final int numCities = BattleBoard.DEFAULT_NUM_CITIES;

    /**
     * Creates a new BattleBoard game object for a set of players.
     * 
     * @param newPlayers a set of GLOReferences to Player GLOs
     */
    protected Game(Set<GLOReference<Player>> newPlayers) {
        SimTask task = SimTask.getCurrent();

        if (newPlayers == null) {
            throw new NullPointerException("newPlayers is null");
        }
        if (newPlayers.size() == 0) {
            throw new IllegalArgumentException("newPlayers is empty");
        }

        gameName = "GameChannel-" +
		SequenceGLO.getNext(task, "GameChannelSequence");

        log.info("New game channel is `" + gameName + "'");

        players = new LinkedList<GLOReference<Player>>(newPlayers);
        Collections.shuffle(players);

        spectators = new LinkedList<GLOReference<Player>>();

        playerBoards = new HashMap<String, GLOReference<Board>>();
        for (GLOReference<Player> playerRef : players) {
            Player p = playerRef.get();
            playerBoards.put(p.getPlayerName(), createBoard(p.getPlayerName()));
        }

        nameToHistory = new HashMap<String, GLOReference<PlayerHistory>>();
        channel = task.openChannel(gameName);
        task.lock(channel, true);

	userCount = 0;
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
	GLOReference<Game> gameRef = task.createGLO(new Game(players));
	ChannelID chan = gameRef.get().channel;

	// Join all the players onto this game's channel
        for (GLOReference<Player> playerRef : players) {
            Player player = playerRef.get();
            player.gameStarted(gameRef);
            task.join(player.getUID(), chan);
        }

	return gameRef;
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

    protected void sendJoinOK() {
        for (GLOReference<Player> ref : players) {
            Player p = ref.peek();
            sendJoinOK(p);
        }
    }

    protected void sendJoinOK(Player player) {
        StringBuffer buf = new StringBuffer("ok ");

        log.finer("playerBoards size " + playerBoards.size());

        GLOReference boardRef = playerBoards.get(player.getPlayerName());
        Board board = (Board) boardRef.peek();

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

        SimTask task = SimTask.getCurrent();
        task.sendData(channel, new UserID[] { player.getUID() },
                byteBuffer.asReadOnlyBuffer(), true);
    }

    protected void sendTurnOrder() {
        StringBuffer buf = new StringBuffer("turn-order ");

        for (GLOReference<Player> playerRef : players) {
            Player p = playerRef.peek();
            buf.append(" " + p.getPlayerName());
        }

        broadcast(buf);
    }

    protected void broadcast(StringBuffer buf) {
        UserID[] uids = new UserID[players.size() + spectators.size()];

        int i = 0;
        for (GLOReference<Player> ref : players) {
            Player p = ref.peek();
            uids[i++] = p.getUID();
        }

        for (GLOReference<Player> ref : spectators) {
            Player p = ref.peek();
            uids[i++] = p.getUID();
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(buf.toString().getBytes());
        byteBuffer.position(byteBuffer.limit());

        log.finest("Game: Broadcasting " + byteBuffer.position() +
		" bytes on " + channel);

        SimTask task = SimTask.getCurrent();
        task.sendData(channel, uids, byteBuffer.asReadOnlyBuffer(), true);
    }

    protected void sendMoveStarted(Player player) {
        StringBuffer buf = new StringBuffer("move-started " +
		player.getPlayerName());
        broadcast(buf);
    }

    protected void startNextMove() {
        log.finest("Running Game.startNextMove");

        currentPlayerRef = players.removeFirst();
        players.addLast(currentPlayerRef);
        Player player = currentPlayerRef.peek();
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
        String bombedPlayerNick = tokens[1];

        GLOReference<Board> boardRef = playerBoards.get(bombedPlayerNick);
        if (boardRef == null) {
            log.warning(player.getPlayerName() +
		    " tried to bomb non-existant player " + bombedPlayerNick);
            handlePass(player);
            return;
        }
        Board board = boardRef.get();

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
                Player winner = playerRef.peek();
                GLOReference<PlayerHistory> historyRef =
			nameToHistory.get(winner.getUserName());
                PlayerHistory history = historyRef.get();
                history.win();
                log.finer(winner.getUserName() + " summary: " +
			history.toString());
            }

            // Someone won, so don't start the next move
            return;
        }

        startNextMove();
    }

    protected void handlePlayerLoss(String loserNick) {
        playerBoards.remove(loserNick);

        Iterator<GLOReference<Player>> i = players.iterator();
        Player loser = null;
        while (i.hasNext()) {
            GLOReference<Player> ref = i.next();
            Player player = ref.peek();
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

        PlayerHistory history = historyRef.get();
        history.lose();

        log.fine(loserNick + " summary: " + history.toString());
    }

    protected void handleResponse(GLOReference<Player> playerRef,
            String[] tokens) {

        if (!playerRef.equals(currentPlayerRef)) {
            log.severe("PlayerRef != CurrentPlayerRef");
            return;
        }

        Player player = playerRef.peek();
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
     * Wait until we get joinedChannel from all our
     * players before starting the game.
     */
    public void joinedChannel(ChannelID cid, UserID uid) {
        log.finer("Game: User " + uid + " joined channel " + cid);

	userCount++;

	if (userCount < players.size()) {
	    return;
	}

        log.finer("Everyone's on the channel, start the game");

        if (log.isLoggable(Level.FINE)) {
            log.finest("playerBoards size " + playerBoards.size());
            for (Map.Entry<String, GLOReference<Board>> x :
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
     * XXX: When a player leaves unexpectedly, we should treat it as a
     * "withdraw" command, when we implement withdraw.
     */
    public void leftChannel(ChannelID cid, UserID uid) {
        log.finer("Game: User " + uid + " left channel " + cid);

        SimTask task = SimTask.getCurrent();

        GLOReference<Player> playerRef = Player.getRef(uid);

        if (playerRef == null) {
            log.warning("No PlayerRef found for uid " + uid);
        }

        // Tell the player this game is over
	Player player = playerRef.get();
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
	    for (GLOReference<? extends GLO> ref : playerBoards.values()) {
		task.destroyGLO(ref);
	    }

	    // Destroy this Game GLO
	    task.destroyGLO(task.lookupReferenceFor(this));
	}
    }
}

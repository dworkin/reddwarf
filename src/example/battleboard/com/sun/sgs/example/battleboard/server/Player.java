package com.sun.sgs.example.battleboard.server;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.ClientSessionListener;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;

/**
 * A server-side BattleBoard Player holds basic data (such as username),
 * and contains login/logout logic. As the SimUserDataListener for a
 * single user, it also dispatches messages to the higher-level Game or
 * Matchmaker that this Player is participating in.
 */
public class Player
    implements ManagedObject, Serializable, ClientSessionListener
{
    private static final long serialVersionUID = 1;

    private static Logger log =
	Logger.getLogger(Player.class.getName());

    /**
     * The username for the user.
     */
    private String myUserName;

    /**
     * The current ClientSession of this user, if logged in.
     */
    private ClientSession mySession;

    /**
     * The name under which the player is playing (i.e., a screen name
     * for this user). This need not be related in any way to the user
     * name!
     */
    private String myPlayerName;

    private ManagedReference myGameRef;

    /**
     * Creates a Player instance with the given userName and uid.
     * 
     * @param userName the name of the user (which is not necessarily
     * the same as the playerName)
     * 
     * @param session the ClientSession of this user
     */
    protected Player(String userName, ClientSession session) {
        myUserName = userName;
        mySession = session;
        myGameRef = null;
        myPlayerName = null;
    }

    public static Player findBySession(ClientSession session) {
        DataManager dataMgr = AppContext.getDataManager();
        String sessionKey = keyForSession(session);
        try {
            return dataMgr.getBinding(sessionKey, Player.class);
        } catch (NameNotBoundException e) {
            return null;
        }
    }

    public String getUserName() {
        return myUserName;
    }

    public ClientSession getSession() {
        return mySession;
    }

    public void setUID(ClientSession session) {
        mySession = session;
    }

    public void gameStarted(Game game) {
        DataManager dataMgr = AppContext.getDataManager();

        myGameRef = dataMgr.createReference(game);

        String playerHistoryName = myUserName + ".history";

        PlayerHistory history;
        try {
            history =
                dataMgr.getBinding(playerHistoryName, PlayerHistory.class);
        } catch (NameNotBoundException e) {
            history = new PlayerHistory(myUserName);
        }
        game.addHistory(history);
    }

    public void gameEnded(ManagedReference gameRef) {
        myGameRef = null;
    }

    public String getPlayerName() {
        return myPlayerName;
    }

    public void setPlayerName(String playerName) {
        myPlayerName = playerName;
    }

    protected static String keyForSession(ClientSession session) {
        return Pattern.compile("\\W+")
                            .matcher(session.toString()).replaceAll("");
    }

    public static Player loggedIn(ClientSession session) {
        log.log(Level.FINER, "User joined server: {0}", session);

        String sessionKey = keyForSession(session);
        String userName = session.getName();
        Player player = new Player(userName, session);

        DataManager dataMgr = AppContext.getDataManager();
        dataMgr.setBinding(sessionKey, player);

        Matchmaker.getInstance().addUserID(session);
        return player;
    }

    public void disconnected(boolean graceful) {
        log.fine("User " + mySession + " left server");
        
        if (myGameRef != null) {
            // We currently support only one game per player
            try {
                Game game = myGameRef.get(Game.class);
                game.playerDisconnected(this);
            } catch (ObjectNotFoundException e) {
                log.fine("Game already gone");
            }
        } else {
            // If no game, dispatch to the matchmaker
            Matchmaker matchmaker = Matchmaker.getInstance();
            matchmaker.playerDisconnected(this);
        }

        String sessionKey = keyForSession(mySession);

        DataManager dataMgr = AppContext.getDataManager();
        dataMgr.removeBinding(sessionKey);
        dataMgr.removeObject(this);
    }

    public void receivedMessage(byte[] message) {
        log.fine("Player: direct data");

        if (myGameRef != null) {
            // We currently support only one game per player
	    Game game = myGameRef.get(Game.class);
	    game.receivedMessage(this, message);
        } else {
            // If no game, dispatch to the matchmaker
            Matchmaker matchmaker = Matchmaker.getInstance();
	    matchmaker.receivedMessage(this, message);
        }
    }
}

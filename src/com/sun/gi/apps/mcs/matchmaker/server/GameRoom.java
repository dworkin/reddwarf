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

package com.sun.gi.apps.mcs.matchmaker.server;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.utils.SGSUUID;

/**
 * <p>
 * Title: GameRoom
 * </p>
 * 
 * <p>
 * Description: Represents a Game Room in the match making application.
 * A GameRoom Channel name is in the form of
 * FolderName.SubFolderName.LobbyName:GameRoomName.
 * </p>
 * 
 * @author Sten Anderson
 * @version 1.0
 */
public class GameRoom extends ChannelRoom {

    private static final long serialVersionUID = 1L;

    private UserID host;
    private HashMap<String, Object> gameParameters;
    private HashMap<UserID, Boolean> userMap; // users mapped to ready state.
    private boolean starting = false;

    public GameRoom(String name, String description, String password,
            String channelName, ChannelID cid, UserID host) {
    	
        super(name, description, password, channelName, cid);

        userMap = new HashMap<UserID, Boolean>();

        this.host = host;
        gameParameters = new HashMap<String, Object>();
    }

    public SGSUUID getGameID() {
        return getChannelID();
    }

    public UserID getHost() {
        return host;
    }

    public void addGameParameter(String key, Object value) {
        gameParameters.put(key, value);
    }
    
    public boolean isStarting() {
    	return starting;
    }
    
    public void setStarting(boolean b) {
    	starting = b;
    }
   

    /**
     * Returns a read-only view of the game parameters map.
     * 
     * @return a read-only view of the game parameters map.
     */
    public Map<String, Object> getGameParamters() {
        return Collections.unmodifiableMap(gameParameters);
    }

    public void addUser(UserID user) {
        if (!userMap.containsKey(user)) {
            userMap.put(user, false);
        }
    }

    /**
     * Updates the "ready state" of the given user for this game.
     * 
     * @param user the user to update
     * @param ready if true, the user is ready
     */
    public void updateReady(UserID user, boolean ready) {
        userMap.put(user, ready);
    }

    public void removeUser(UserID user) {
        userMap.remove(user);
    }

    public List<UserID> getUsers() {
        List<UserID> playerList = new LinkedList<UserID>(userMap.keySet());

        return playerList;
    }

    public void removeAllUsers() {
        userMap.clear();
    }

    public int getNumPlayers() {
        return userMap.size();
    }
    
    public boolean isPlayerReady(UserID userID) {
    	return userMap.get(userID);
    }

    /**
     * Returns true if all joined players have indicated that they are
     * ready.
     * 
     * @return true if all players are ready for game start.
     */
    public boolean arePlayersReady() {
        return !userMap.containsValue(false);
    }

}

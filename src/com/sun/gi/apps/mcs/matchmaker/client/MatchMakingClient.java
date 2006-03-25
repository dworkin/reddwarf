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

package com.sun.gi.apps.mcs.matchmaker.client;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.security.auth.callback.Callback;

import static com.sun.gi.apps.mcs.matchmaker.common.CommandProtocol.*;

import com.sun.gi.apps.mcs.matchmaker.common.CommandProtocol;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;

/**
 * <p>
 * Title: MatchMakingClient
 * </p>
 * 
 * <p>
 * Description: This class is a concrete implementation of the
 * IMatchMakingClient. It communicates with the match making server
 * application via the ClientConnectionManager for sending commands, and
 * listening on the well known lobby control channel for receiving
 * responses.
 * </p>
 * 
 * @author Sten Anderson
 * @version 1.0
 */
public class MatchMakingClient
        implements IMatchMakingClient, ClientConnectionManagerListener,
                    ClientChannelListener {
	
    private IMatchMakingClientListener listener;
    private ClientConnectionManager manager;
    private HashMap<String, LobbyChannel> lobbyMap;
    private HashMap<String, GameChannel> gameMap;

    private CommandProtocol protocol;
    private byte[] myID;

    /**
     * Constructs a new MatchMakingClient.
     * 
     * @param manager the ClientConnectionManager used for server
     * commmunication
     */
    public MatchMakingClient(ClientConnectionManager manager) {
        this.manager = manager;
        manager.setListener(this);
        protocol = new CommandProtocol();
        lobbyMap = new HashMap<String, LobbyChannel>();
        gameMap = new HashMap<String, GameChannel>();
    }

    /**
     * Sends a command to the server via the ClientConnectionManager.
     * 
     * @param list			the list containing the components of the request packet
     */
    void sendCommand(List list) {
        manager.sendToServer(protocol.assembleCommand(list), true);
    }

    protected void packGameParamters(List list, HashMap<String, Object> gameParameters) {
        list.add(gameParameters.size());

        for (Map.Entry<String, Object> entry : gameParameters.entrySet()) {
            String curKey = entry.getKey();
            list.add(curKey);
            Object value = entry.getValue();
            list.add(protocol.mapType(value));
            if (value instanceof byte[]) {
            	list.add(protocol.createUUID((byte[]) value));
            }
            else { 
            	list.add(value);
            }
        }
    }
    
    /**
     * Returns true if the given id is the server's ID.  This is a pass-through
     * to the ClientConnectionManager for other classes in the match maker
     * client.
     * 
     * @param id			the ID to compare to the server's
     * 
     * @return true if the given ID is also the server's ID.
     */
    public boolean isServerID(byte[] id) {
    	return manager.isServerID(id);
    }

    public void setListener(IMatchMakingClientListener listener) {
        this.listener = listener;
    }

    public void listFolder(byte[] folderID) {
        List list = protocol.createCommandList(LIST_FOLDER_REQUEST);
        if (folderID != null) {
            list.add(protocol.createUUID(folderID));
        }

        sendCommand(list);
    }

    public void joinLobby(byte[] lobbyID, String password) {
        List list = protocol.createCommandList(JOIN_LOBBY);
        list.add(protocol.createUUID(lobbyID));
        if (password != null) {
            list.add(password);
        }

        sendCommand(list);
    }

    public void joinGame(byte[] gameID) {
        joinGame(gameID, null);
    }

    public void joinGame(byte[] gameID, String password) {
        List list = protocol.createCommandList(JOIN_GAME);
        list.add(protocol.createUUID(gameID));
        if (password != null) {
            list.add(password);
        }

        sendCommand(list);
    }
    
    public void leaveLobby() {
    	sendCommand(protocol.createCommandList(LEAVE_LOBBY));
    }
    
    public void leaveGame() {
    	sendCommand(protocol.createCommandList(LEAVE_GAME));
    }
    
    public void completeGame(byte[] gameID) {
    	List list = protocol.createCommandList(GAME_COMPLETED);
    	list.add(protocol.createUUID(gameID));
    	
    	sendCommand(list);
    }
    
    /**
     * Attempts to find the user name of a currently connected user with
     * the given ID.
     * 
     * @param userID
     */
    public void lookupUserName(byte[] userID) {
        List list = protocol.createCommandList(LOOKUP_USER_NAME_REQUEST);
        list.add(protocol.createUUID(userID));

        sendCommand(list);
    }

    /**
     * Attempts to find the user ID of a currently connected user with
     * the given user name.
     * 
     * @param username
     */
    public void lookupUserID(String username) {
        List list = protocol.createCommandList(LOOKUP_USER_ID_REQUEST);
        list.add(username);

        sendCommand(list);
    }

    public void sendValidationResponse(Callback[] cb) {
        manager.sendValidationResponse(cb);
    }

    // implemented methods from ClientConnectionManagerListener

    public void validationRequest(Callback[] callbacks) {
        listener.validationRequest(callbacks);
    }

    public void connected(byte[] myID) {
        this.myID = myID;
    }

    public void connectionRefused(String message) {
    }

    public void failOverInProgress() {

    }

    public void reconnected() {

    }

    public void disconnected() {
        listener.disconnected();
    }

    /**
     * {@inheritDoc}
     */
    public void userJoined(byte[] userID) {

    }

    /**
     * {@inheritDoc}
     */
    public void userLeft(byte[] userID) {

    }

    /**
     * {@inheritDoc}
     */
    public void joinedChannel(ClientChannel channel) {
        if (channel.getName().equals(LOBBY_MANAGER_CONTROL_CHANNEL)) {
            channel.setListener(this);
        } else if (channel.getName().indexOf(":") == -1) { // lobby
            LobbyChannel lobby = new LobbyChannel(channel, this);
            lobbyMap.put(channel.getName(), lobby);
            channel.setListener(lobby);
            listener.joinedLobby(lobby);
        } else { // game
            GameChannel game = new GameChannel(channel, this);
            gameMap.put(channel.getName(), game);
            channel.setListener(game);
            listener.joinedGame(game);
        }

    }

    /**
     * {@inheritDoc}
     */
    public void channelLocked(String channelName, byte[] userID) {

    }

    // implemented methods from ClientChannelListener
    // these call backs are on the LobbyManager control channel

    /**
     * {@inheritDoc}
     */
    public void playerJoined(byte[] playerID) {

    }

    /**
     * {@inheritDoc}
     */
    public void playerLeft(byte[] playerID) {

    }

    /**
     * {@inheritDoc}
     */
    public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
        if (!isServerID(from)) { 
        	return; 
        }
         
        int command = protocol.readUnsignedByte(data);
        if (command == SERVER_LISTENING) {
            listener.connected(myID);
        } else if (command == LIST_FOLDER_RESPONSE) {
            listFolderResponse(data);
        } else if (command == LOOKUP_USER_NAME_RESPONSE) {
            lookupUserNameResponse(data);
        } else if (command == LOOKUP_USER_ID_RESPONSE) {
            lookupUserIDResponse(data);
        } else if (command == GAME_PARAMETERS_RESPONSE) {
            gameParametersResponse(data);
        } else if (command == CREATE_GAME_FAILED) {
            createGameFailed(data);
        } else if (command == ERROR) {
        	listener.error(protocol.readUnsignedByte(data));
        } else if (command == LEFT_LOBBY) {
        	listener.leftLobby();
        } else if (command == LEFT_GAME) {
        	listener.leftGame();
        }
        
    }

    /**
     * {@inheritDoc}
     */
    public void channelClosed() {
    }

    /**
     * Called to parse out the ListFolderResponse response from the
     * server.
     * 
     * @param data the data buffer containing the requested folder and
     * lobby detail.
     */
    private void listFolderResponse(ByteBuffer data) {
        byte[] folderID = protocol.readUUIDAsBytes(data);
        int numFolders = data.getInt();
        FolderDescriptor[] subfolders = new FolderDescriptor[numFolders];
        for (int i = 0; i < numFolders; i++) {
            String curFolderName = protocol.readString(data);
            String curFolderDescription = protocol.readString(data);
            byte[] curFolderID = protocol.readUUIDAsBytes(data);
            subfolders[i] = new FolderDescriptor(curFolderID, curFolderName,
                    curFolderDescription);
        }
        int numLobbies = data.getInt();
        LobbyDescriptor[] lobbies = new LobbyDescriptor[numLobbies];
        for (int i = 0; i < numLobbies; i++) {
            String curLobbyName = protocol.readString(data);
            String curLobbyChannelName = protocol.readString(data);
            String curLobbyDescription = protocol.readString(data);
            byte[] curLobbyID = protocol.readUUIDAsBytes(data);
            int numUsers = data.getInt();
            int maxUsers = data.getInt();
            boolean isPasswordProtected = protocol.readBoolean(data);

            lobbies[i] = new LobbyDescriptor(curLobbyID, curLobbyName,
                    curLobbyChannelName, curLobbyDescription, numUsers,
                    maxUsers, isPasswordProtected);
        }
        listener.listedFolder(folderID, subfolders, lobbies);
    }

    private void lookupUserNameResponse(ByteBuffer data) {
        String userName = protocol.readString(data);
        byte[] userID = protocol.readUUIDAsBytes(data);

        listener.foundUserName(userName, userID);
    }

    private void lookupUserIDResponse(ByteBuffer data) {
        String userName = protocol.readString(data);
        byte[] userID = protocol.readUUIDAsBytes(data);

        listener.foundUserID(userName, userID);
    }

    private void gameParametersResponse(ByteBuffer data) {
        String lobbyName = protocol.readString(data);
        int numParams = data.getInt();
        HashMap<String, Object> paramMap = new HashMap<String, Object>();
        for (int i = 0; i < numParams; i++) {
            String param = protocol.readString(data);
            Object value = protocol.readParamValue(data, true);
            paramMap.put(param, value);
        }
        LobbyChannel lobby = lobbyMap.get(lobbyName);
        if (lobby != null) {
            lobby.receiveGameParameters(paramMap);
        }
    }

    private void createGameFailed(ByteBuffer data) {
        String game = protocol.readString(data);
        int errorCode = protocol.readUnsignedByte(data);
        String lobbyName = protocol.readString(data);
        if (lobbyName == null) {
            return;
        }
        LobbyChannel lobby = lobbyMap.get(lobbyName);
        if (lobby != null) {
            lobby.createGameFailed(game, errorCode);
        }
    }
}

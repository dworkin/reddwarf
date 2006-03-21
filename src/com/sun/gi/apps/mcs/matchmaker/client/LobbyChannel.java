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
import java.util.List;
import java.util.Map;

import static com.sun.gi.apps.mcs.matchmaker.common.CommandProtocol.*;

import com.sun.gi.apps.mcs.matchmaker.common.CommandProtocol;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.utils.SGSUUID;
import com.sun.gi.utils.StatisticalUUID;

/**
 * 
 * <p>Title: LobbyChannel</p>
 * 
 * <p>Description: An implementation of ILobbyChannel that uses SGS
 * ClientChannels for communication.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class LobbyChannel implements ILobbyChannel, ClientChannelListener {

    private ClientChannel channel;
    private ILobbyChannelListener listener;
    private CommandProtocol protocol;
    private MatchMakingClient mmClient;

    public LobbyChannel(ClientChannel chan, MatchMakingClient client) {
        this.channel = chan;
        protocol = new CommandProtocol();
        this.mmClient = client;
    }

    public void setListener(ILobbyChannelListener listener) {
        this.listener = listener;
    }

    public void sendText(String text) {
        List list = protocol.createCommandList(SEND_TEXT);
        list.add(text);
        ByteBuffer buffer = protocol.assembleCommand(list);
        channel.sendBroadcastData(buffer, true);
    }

    public void sendPrivateText(byte[] user, String text) {
        List list = protocol.createCommandList(SEND_PRIVATE_TEXT);
        list.add(createUserID(user));
        list.add(text);
        ByteBuffer buffer = protocol.assembleCommand(list);

        channel.sendUnicastData(user, buffer, true);
    }

    private SGSUUID createUserID(byte[] bytes) {
        SGSUUID id = null;
        try {
            id = new StatisticalUUID(bytes);
        } catch (InstantiationException ie) {}

        return id;
    }

    public void requestGameParameters() {
        List list = protocol.createCommandList(GAME_PARAMETERS_REQUEST);

        mmClient.sendCommand(list);
    }

    public void createGame(String name, String description, String password,
            HashMap<String, Object> gameParameters) {
    	
    	if (name == null || gameParameters == null) {
    		return;
    	}
        List list = protocol.createCommandList(CREATE_GAME);
        list.add(name);
        list.add(description);
        list.add(password != null);
        if (password != null) {
            list.add(password);
        }
        list.add(gameParameters.size());

        for (Map.Entry<String, Object> entry : gameParameters.entrySet()) {
            String curKey = entry.getKey();
            list.add(curKey);
            Object value = entry.getValue();
            list.add(protocol.mapType(value));
            list.add(value);
        }
        mmClient.sendCommand(list);
    }

    void receiveGameParameters(HashMap<String, Object> params) {
        if (listener != null) {
            listener.receivedGameParameters(params);
        }
    }

    void createGameFailed(String game, int errorCode) {
        if (listener != null) {
            listener.createGameFailed(game, errorCode);
        }
    }

    // implemented methods from ClientChannelListener

    /**
     * A new player has joined the channel this listener is registered
     * on.
     * 
     * @param playerID The ID of the joining player.
     */
    public void playerJoined(byte[] playerID) {}

    /**
     * A player has left the channel this listener is registered on.
     * 
     * @param playerID The ID of the leaving player.
     */
    public void playerLeft(byte[] playerID) {
        if (listener != null) {
            listener.playerLeft(playerID);
        }
    }

    /**
     * A packet has arrived for this listener on this channel.
     * 
     * @param from the ID of the sending player.
     * @param data the packet data
     * @param reliable true if this packet was sent reliably
     */
    public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
        if (listener == null) { // if no one is listening, no reason to
                                // do anything
            return;
        }
        int command = protocol.readUnsignedByte(data);
        if (command == SEND_TEXT) {
        	listener.receiveText(from, protocol.readString(data), false);
        } else if (command == SEND_PRIVATE_TEXT) {
        	SGSUUID id = protocol.readUserID(data);
        	listener.receiveText(from, protocol.readString(data), true);
        
        }	
        if (!mmClient.isServerID(from)) {
        	return;
        }
        
        if (command == PLAYER_ENTERED_LOBBY) {
            byte[] userID = protocol.readUUIDAsBytes(data);
            String name = protocol.readString(data);
            listener.playerEntered(userID, name);
        } else if (command == GAME_CREATED) {

            listener.gameCreated(readGameDescriptor(data));
        } else if (command == PLAYER_JOINED_GAME) {
            byte[] userID = protocol.readUUIDAsBytes(data);
            byte[] gameID = protocol.readUUIDAsBytes(data);
            listener.playerJoinedGame(gameID, userID);
        } else if (command == GAME_STARTED) {
        	listener.gameStarted(readGameDescriptor(data));
        } else if (command == GAME_DELETED) {
        	listener.gameDeleted(readGameDescriptor(data));
        }
    }
    
    private GameDescriptor readGameDescriptor(ByteBuffer data) {
        byte[] uuid = protocol.readUUIDAsBytes(data);
        String name = protocol.readString(data);
        String description = protocol.readString(data);
        String channelName = protocol.readString(data);
        boolean isProtected = protocol.readBoolean(data);
        int maxPlayers = data.getInt();
        int numParams = data.getInt();
        HashMap<String, Object> paramMap = new HashMap<String, Object>();
        for (int i = 0; i < numParams; i++) {
            String param = protocol.readString(data);
            Object value = protocol.readParamValue(data);
            paramMap.put(param, value);
        }
       return  new GameDescriptor(uuid, name, description,
                channelName, isProtected, paramMap);
    }

    /**
     * Notifies this listener that the channel has been closed.
     */
    public void channelClosed() {

    }

    public String getName() {
        return channel.getName();
    }
}

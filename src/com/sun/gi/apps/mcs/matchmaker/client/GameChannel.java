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
 * <p>Title: GameChannel</p>
 * 
 * <p>Description: An implementation of IGameChannel that uses SGS ClientChannels 
 * for its communication.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public class GameChannel implements IGameChannel, ClientChannelListener {

    private IGameChannelListener listener;
    private MatchMakingClient mmClient;
    private ClientChannel channel;
    private CommandProtocol protocol;

    public GameChannel(ClientChannel chan, MatchMakingClient client) {
        this.channel = chan;
        protocol = new CommandProtocol();
        this.mmClient = client;
    }

    public void setListener(IGameChannelListener listener) {
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

    public void playerJoined(byte[] playerID) {

    }

    public void playerLeft(byte[] playerID) {
    	if (listener != null) {
    		listener.playerLeft(playerID);
    	}
    }

    public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
        if (listener == null) {
            // if no one is listening, no reason to do anything
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
        
        if (command == PLAYER_ENTERED_GAME) {
            byte[] userID = protocol.readUUIDAsBytes(data);
            String name = protocol.readString(data);
            listener.playerEntered(userID, name);
        } else if (command == PLAYER_READY_UPDATE) {
            byte[] userID = protocol.readUUIDAsBytes(data);
            boolean ready = protocol.readBoolean(data);
            listener.playerReady(userID, ready);
        } else if (command == START_GAME_REQUEST) {
            // means there was a failure.
            listener.startGameFailed(protocol.readString(data));
        } else if (command == GAME_STARTED) {
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
            GameDescriptor game =
                new GameDescriptor(uuid, name, description, channelName,
                        isProtected, paramMap);
            listener.gameStarted(game);
        }

    }

    public void channelClosed() {
    	if (listener != null) {
    		listener.gameCompleted();
    	}
    }

    public String getName() {
        return channel.getName();
    }

    public void ready(GameDescriptor game, boolean ready) {
        List list = protocol.createCommandList(UPDATE_PLAYER_READY_REQUEST);
        list.add(ready);
        if (ready) {
            HashMap<String, Object> gameParameters = game.getGameParameters();
            list.add(gameParameters.size());
            for (Map.Entry<String, Object> entry : gameParameters.entrySet()) {
                String curKey = entry.getKey();
                list.add(curKey);
                Object value = entry.getValue();
                list.add(protocol.mapType(value));
                list.add(value);
            }
        }
        mmClient.sendCommand(list);
    }

    public void startGame() {
        List list = protocol.createCommandList(START_GAME_REQUEST);
        mmClient.sendCommand(list);
    }
}

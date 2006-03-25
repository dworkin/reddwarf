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

import static com.sun.gi.apps.mcs.matchmaker.common.CommandProtocol.*;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;

import com.sun.gi.apps.mcs.matchmaker.common.CommandProtocol;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.comm.users.client.ClientChannel;

/**
 * 
 * <p>Title: ChannelRoom</p>
 * 
 * <p>Description: Common super class for Lobbies and Game rooms.</p>
 * 
 * <p>Copyright: Copyright (c) 2006</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public abstract class ChannelRoom implements IChannelRoom {

    private ClientChannel channel;
    protected CommandProtocol protocol;
    private MatchMakingClient mmClient;
    
    public ChannelRoom(ClientChannel channel, MatchMakingClient mmClient) {
    	this.channel = channel;
    	protocol = new CommandProtocol();
    	this.mmClient = mmClient;
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
    
    protected void packGameParameters(List list, HashMap<String, Object> params) {
    	mmClient.packGameParamters(list, params);
    }

    protected UserID createUserID(byte[] bytes) {
        UserID id = null;
        try {
            id = new UserID(bytes);
        } catch (InstantiationException ie) {}

        return id;
    }
    
    protected void sendCommand(List commandList) {
    	mmClient.sendCommand(commandList);
    }
    
    /**
     * Notifies this listener that the channel has been closed.
     */
    public void channelClosed() {

    }

    public String getName() {
        return channel.getName();
    }
    
    public void playerJoined(byte[] playerID) {

    }
    
    /**
     * Attempts to process an incoming command.  Returns true if it consumes
     * the command.
     * 
     * @param command
     * @param listener
     * @param from
     * @param data
     * 
     * @return true if it was able to process the command, or if no
     * 			further action should be taken on it.
     */
    protected boolean processCommand(int command, IChannelRoomListener listener, byte[] from, ByteBuffer data) {
    	if (command == SEND_TEXT) {
        	listener.receiveText(from, protocol.readString(data), false);
        	return true;
        } else if (command == SEND_PRIVATE_TEXT) {
        	UserID id = protocol.readUserID(data);
        	listener.receiveText(from, protocol.readString(data), true);
        	return true;
        }
        if (!mmClient.isServerID(from)) {
        	return true;
        }
        
        if (command == PLAYER_ENTERED_GAME) {
            byte[] userID = protocol.readUUIDAsBytes(data);
            String name = protocol.readString(data);
            listener.playerEntered(userID, name);
            return true;
        } else if (command == GAME_STARTED) {
            listener.gameStarted(readGameDescriptor(data));
            return true;
        } else if (command == PLAYER_BOOTED_FROM_GAME) {
        	byte[] booter = protocol.readUUIDAsBytes(data);
        	byte[] bootee = protocol.readUUIDAsBytes(data);
        	boolean isBanned = protocol.readBoolean(data);
        	listener.playerBootedFromGame(booter, bootee, isBanned);
        	return true;
        } else if (command == GAME_UPDATED) {
        	GameDescriptor game = readGameDescriptor(data);
        	listener.gameUpdated(game);
        	return true;
        }
        return false;
    }
    
    protected GameDescriptor readGameDescriptor(ByteBuffer data) {
        byte[] uuid = protocol.readUUIDAsBytes(data);
        String name = protocol.readString(data);
        String description = protocol.readString(data);
        String channelName = protocol.readString(data);
        boolean isProtected = protocol.readBoolean(data);
        int maxPlayers = data.getInt();
        
        return new GameDescriptor(uuid, name, description, channelName,
                    		maxPlayers, isProtected, readGameParameters(data));
    }
    
    protected HashMap<String, Object> readGameParameters(ByteBuffer data) {
        int numParams = data.getInt();
        HashMap<String, Object> paramMap = new HashMap<String, Object>();
        for (int i = 0; i < numParams; i++) {
            String param = protocol.readString(data);
            Object value = protocol.readParamValue(data, true);
            paramMap.put(param, value);
        }
        return paramMap;
    }
    
}

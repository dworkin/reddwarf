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

package com.sun.gi.apps.mcs.matchmaker.client;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.sun.gi.apps.mcs.matchmaker.server.CommandProtocol.*;

import com.sun.gi.apps.mcs.matchmaker.server.CommandProtocol;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientChannelListener;
import com.sun.gi.utils.SGSUUID;

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

    }

    public void playerJoined(byte[] playerID) {

    }

    public void playerLeft(byte[] playerID) {

    }

    public void dataArrived(byte[] from, ByteBuffer data, boolean reliable) {
        if (listener == null) {
            // if no one is listening, no reason to do anything
            return;
        }
        // TODO sten: test isServerID once that works
        int command = protocol.readUnsignedByte(data);
        if (command == PLAYER_ENTERED_GAME) {
            SGSUUID userID = protocol.readUUID(data);
            String name = protocol.readString(data);
            listener.playerEntered(userID.toByteArray(), name);
        } else if (command == PLAYER_READY_UPDATE) {
            SGSUUID userID = protocol.readUUID(data);
            boolean ready = protocol.readBoolean(data);
            listener.playerReady(userID.toByteArray(), ready);
        } else if (command == START_GAME_REQUEST) {
            // means there was a failure.
            System.out.println("game channel: start game failed");
            listener.startGameFailed(protocol.readString(data));
        } else if (command == GAME_STARTED) {
            SGSUUID uuid = protocol.readUUID(data);
            String name = protocol.readString(data);
            String description = protocol.readString(data);
            String channelName = protocol.readString(data);
            boolean isProtected = protocol.readBoolean(data);
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

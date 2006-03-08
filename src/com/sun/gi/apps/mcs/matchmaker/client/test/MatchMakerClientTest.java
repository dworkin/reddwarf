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

package com.sun.gi.apps.mcs.matchmaker.client.test;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.NameCallback;

import com.sun.gi.apps.mcs.matchmaker.client.FolderDescriptor;
import com.sun.gi.apps.mcs.matchmaker.client.GameDescriptor;
import com.sun.gi.apps.mcs.matchmaker.client.IGameChannel;
import com.sun.gi.apps.mcs.matchmaker.client.IGameChannelListener;
import com.sun.gi.apps.mcs.matchmaker.client.ILobbyChannel;
import com.sun.gi.apps.mcs.matchmaker.client.ILobbyChannelListener;
import com.sun.gi.apps.mcs.matchmaker.client.IMatchMakingClient;
import com.sun.gi.apps.mcs.matchmaker.client.IMatchMakingClientListener;
import com.sun.gi.apps.mcs.matchmaker.client.LobbyDescriptor;
import com.sun.gi.apps.mcs.matchmaker.client.MatchMakingClient;
import com.sun.gi.comm.discovery.impl.URLDiscoverer;
import com.sun.gi.comm.users.client.ClientChannel;
import com.sun.gi.comm.users.client.ClientConnectionManager;
import com.sun.gi.comm.users.client.ClientConnectionManagerListener;
import com.sun.gi.comm.users.client.impl.ClientConnectionManagerImpl;
import com.sun.gi.utils.SGSUUID;

/**
 * <p>
 * Title: MatchMakerClientTest
 * </p>
 * 
 * <p>
 * Description: Test harness for the J2SE match making client.
 * </p>
 * 
 * @author Sten Anderson
 * @version 1.0
 */
public class MatchMakerClientTest implements IMatchMakingClientListener {

    private IMatchMakingClient mmClient;

    private int numTimes = 0;

    public MatchMakerClientTest() {}

    public void connect() {
        try {
            ClientConnectionManager manager = new ClientConnectionManagerImpl(
                    "MatchMaker", new URLDiscoverer(new File(
                            "resources/FakeDiscovery.xml").toURI().toURL()));
            mmClient = new MatchMakingClient(manager);
            mmClient.setListener(this);
            String[] classNames = manager.getUserManagerClassNames();
            manager.connect(classNames[0]);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    public static void main(String[] args) {
        new MatchMakerClientTest().connect();
    }

    // implemented methods from IMatchMakingClientListener

    /**
     * This call-back is called by the associated IMatchMakingClient in
     * response to a listFolder command.
     * 
     * @param folderID the UUID of the requested folder
     * @param subFolders an array of sub folders contained by the
     * requested folder
     * @param lobbies an array of lobbies contained by the requested
     * folder
     */
    public void listedFolder(SGSUUID folderID, FolderDescriptor[] subFolders,
            LobbyDescriptor[] lobbies) {
        System.out.println("Listed folder: folderID: " + folderID);
        for (FolderDescriptor f : subFolders) {
            System.out.println("\tFolder: " + f.getName() + " "
                    + f.getDescription() + " " + f.getFolderID());
            mmClient.listFolder(f.getFolderID().toByteArray());
        }
        /*
         * for (LobbyDescriptor l : lobbies) {
         * System.out.println("Lobby: " + l.getName() + " " +
         * l.getDescription() + " " + l.getNumUsers() + " " +
         * l.getMaxUsers() + l.isPasswordProtected() + " " +
         * l.getLobbyID()); }
         */

        /*
         * if (subFolders.length > 0 && numTimes == 0) { //numTimes++;
         * mmClient.listFolder(subFolders[0].getFolderID().toByteArray()); }
         */
        /*
         * if(lobbies.length > 0) {
         * mmClient.joinLobby(lobbies[0].getLobbyID().toByteArray(),
         * "secret"); }
         */
    }

    public void foundUserName(String userName, byte[] userID) {
        System.out.println("foundUserName: " + userName + " userID "
                + userID.toString());
    }

    public void foundUserID(String userName, byte[] userID) {
        System.out.println("foundUserID: " + userName + " userID " + userID);
    }

    public void joinedLobby(final ILobbyChannel channel) {
        System.out.println("MatchMakerClientTest: joined Lobby ");
        channel.setListener(new ILobbyChannelListener() {
            public void playerEntered(byte[] player, String name) {
                System.out.println("playerEntered " + name);

                channel.sendText("hi there " + name);
            }

            public void playerLeft(byte[] player) {
                System.out.println("playerLeft " + player);
            }

            public void receiveText(byte[] from, String text, boolean wasPrivate) {
                System.out.println("received text " + text + " wasPrivate "
                        + wasPrivate);
            }

            public void receivedGameParameters(
                    HashMap<String, Object> parameters) {
                System.out.println("Test client receive game params");
                Iterator<String> iterator = parameters.keySet().iterator();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    System.out.println("Game Parameter: " + key + " value "
                            + parameters.get(key));
                }
                channel.createGame("Test Game", "Test Description", null,
                        parameters);
            }

            public void createGameFailed(String name, String reason) {
                System.out.println("LobbyChannelListener: createGameFailed "
                        + name + " reason " + reason);
            }

            public void gameCreated(GameDescriptor game) {
                System.out.println("Game Created " + game.getName() + " "
                        + game.getDescription());
                HashMap<String, Object> parameters = game.getGameParameters();
                Iterator<String> iterator = parameters.keySet().iterator();
                while (iterator.hasNext()) {
                    String key = iterator.next();
                    System.out.println("Game Parameter: " + key + " value "
                            + parameters.get(key));
                }
            }

            public void playerJoinedGame(byte[] gameID, byte[] player) {
                System.out.println("LobbyChannelListener: playerJoinedGame ");
            }
        });
        channel.requestGameParameters();
    }

    public void joinedGame(IGameChannel channel) {
        System.out.println("Match Maker Test joinedGame");
        channel.setListener(new IGameChannelListener() {
            public void playerEntered(byte[] player, String name) {
                System.out.println("IGameChannelListener playerEntered " + name);
            }

            public void playerLeft(byte[] player) {
                System.out.println("IGameChannelListener playerLeft");
            }

            public void receiveText(byte[] from, String text, boolean wasPrivate) {
                System.out.println("IGameChannelListener " + text +
                        " wasPrivate " + wasPrivate);
            }

            public void playerReady(byte[] player, boolean ready) {

            }

            public void startGameFailed(String reason) {

            }

            public void gameStarted(GameDescriptor game) {

            }
        });
    }

    public void connected(byte[] myID) {
        System.out.println("Client received connection notification");
        mmClient.listFolder(null);

        // mmClient.lookupUserName(myID);
        // mmClient.lookupUserID("gust");
    }

    public void disconnected() {}

    public void validationRequest(Callback[] callbacks) {
        System.out.println("validation request");
        for (Callback cb : callbacks) {
            if (cb instanceof NameCallback) {
                ((NameCallback) cb).setName("Guest");
            }
        }
        mmClient.sendValidationResponse(callbacks);
    }

}
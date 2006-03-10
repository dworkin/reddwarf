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

import javax.security.auth.callback.Callback;

/**
 * 
 * <p>
 * Title: IMatchMakingClient
 * </p>
 * 
 * <p>
 * Description: This is the main interface that communicates with the
 * server match making application.
 * </p>
 * 
 * @author Sten Anderson
 * @version 1.0
 */
public interface IMatchMakingClient {

    /**
     * Sets the listener to receive the server command responses as
     * call-backs.
     * 
     * @param listener the listener
     */
    public void setListener(IMatchMakingClientListener listener);

    /**
     * Called to list the contents (folders and lobbies) of the folder
     * with the matching Folder ID. Maps to the server command,
     * ListFolder. The ListedFolder command response is expected from
     * the server and translated to the
     * IMatchMakingClientListener.listedFolder callback.
     * 
     * @param folderID the folder's unique identifier. Pass in null for
     * the root listing.
     */
    public void listFolder(byte[] folderID);

    /**
     * Attempts to find the user name of a currently connected user with
     * the given ID.
     * 
     * @param userID
     */
    public void lookupUserName(byte[] userID);

    /**
     * Attempts to find the user ID of a currently connected user with
     * the given user name.
     * 
     * @param username
     */
    public void lookupUserID(String username);

    /**
     * Attempts to join to the lobby with the given lobbyID.
     * 
     * @param lobbyID			the unique ID of the lobby to join 
     * @param password			the password to access the lobby
     */
    public void joinLobby(byte[] lobbyID, String password);

    /**
     * Attempts to join a non-password protected game room given
     * by the gameID.
     * 
     * @param gameID			the unique ID of the game room
     */
    public void joinGame(byte[] gameID);

    
    /**
     * Attempts to join a password protected game room given by gameID.
     * 
     * @param gameID			the unique ID of the game room
     * @param password			the game room's password
     */
    public void joinGame(byte[] gameID, String password);
    
    /**
     * Attempts to leave the currently connected lobby.  The client should not
     * assume it has left the lobby until it receives the 
     * IMatchMakingClientListener.leftLobby() callback.
     */
    public void leaveLobby();
    
    /**
     * Attempts to leave the currently connected game room.  The client should not
     * assume it has left the game room until it receives the 
     * IMatchMakingClientListener.leftLobby() callback.
     */
    public void leaveGame();

    /**
     * Called as a pass-through to the ClientConnectionManager during
     * login authentication.  The client should set the call backs as
     * appropriate.
     * 
     * @param cb 				the security callbacks
     */
    public void sendValidationResponse(Callback[] cb);

}

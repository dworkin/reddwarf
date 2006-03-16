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

import javax.security.auth.callback.Callback;


/**
 * <p>
 * Title: IMatchMakingClientListener
 * </p>
 * 
 * <p>
 * Description: Clients should implement this interface to receive
 * command call-backs from the server application. When the associated
 * IMatchMakingClient receives a command response from the server, it
 * calls the appropriate call-back on this listener.
 * </p>
 * 
 * @author Sten Anderson
 * @version 1.0
 */
public interface IMatchMakingClientListener {

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
    public void listedFolder(byte[] folderID, FolderDescriptor[] subFolders,
            LobbyDescriptor[] lobbies);

    /**
     * Called in response to the LookupUserName request.
     * 
     * @param userName			the username matching the given ID, or an empty
     * 							String if not found
     * @param userID			the user ID used in the original request
     */
    public void foundUserName(String userName, byte[] userID);

    /**
     * Called in response to the LookupUserIDRequest.
     * 
     * @param userName			the username used in the original request
     * @param userID			the user ID matching the username, or null if not found.
     */
    public void foundUserID(String userName, byte[] userID);

    /**
     * Called when the user is joined to a lobby.  The given channel can be
     * used to issue commands on the lobby.
     * 
     * @param channel			the channel of communication with the lobby
     */
    public void joinedLobby(ILobbyChannel channel);

    /**
     * Called when the user is joined to a game.  The given channel can be 
     * used to issue commands to the game.
     * 
     * @param channel			the channel of communication with the game
     */
    public void joinedGame(IGameChannel channel);
    
    /**
     * A confirmation call-back that the user has left whichever lobby
     * they were previously connected to.
     *
     */
    public void leftLobby();
    
    /**
     * A confirmation call-back that the user has left whichever game
     * room they were previously connected to.
     *
     */
    public void leftGame();

    /**
     * Called when the client has successfully connected to the server
     * and received confirmation that it is joined to the LobbyManager
     * control channel.
     */
    public void connected(byte[] myID);

    /**
     * Called as confirmation that the client has disconnected from the server.
     * The disconnect may or may not have been client initiated.
     *
     */
    public void disconnected();

    /**
     * Called when a request for login authentication comes from the
     * server.
     * 
     * @param callbacks the security call backs
     */
    public void validationRequest(Callback[] callbacks);
    
    /**
     * Called when some command request encounters an error.  The error code
     * is defined in CommandProtocol.
     * 
     * @param errorCode		a code detailing the error condition
     */
    public void error(int errorCode);
}

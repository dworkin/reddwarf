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

package com.sun.gi.comm.users.client;

import java.nio.ByteBuffer;

import javax.security.auth.callback.Callback;

/**
 * The UserManagerClientListener interface handles callbacks from
 * a UserManagerClient.
 * 
 * @author Jeffrey P. Kesselman
 * @version 1.0
 * 
 * @see UserManagerClient
 */
public interface UserManagerClientListener {

    /**
     * Fired when a connection to the SGS backend has been
     * successfully made.
     */
    public void connected();

    /**
     * Fired when the connection to the SGS backend is lost.
     */
    public void disconnected();

    /**
     * Informs the event listener of the issuance of a new time-limited
     * reconnection key.
     * 
     * @param key the reconnection key.
     * @param ttl the number of seconds this key is valid for. Note that
     * this is seconds from time of issue and so is a maximum not an
     * absolute measure due to delays in delivery.
     */
    public void newConnectionKeyIssued(byte[] key, long ttl);

    /**
     * Informs the event listener that further information is needed in
     * order to validate the user.
     * 
     * @param cbs an array of JAAS Callback structures to be filled out
     * and sent back.
     */
    public void validationDataRequest(Callback[] cbs);

    /**
     * This event informs the event listener that the validation has
     * processed successfully and that the user can start communicating
     * with the back-end and other users.
     * <p>
     * <b>NOTE: UserIDs are not gauranteed to remain the same between
     * logins.</b>
     * 
     * @param userID An ID issued to represent this user
     */
    public void loginAccepted(byte[] userID);

    /**
     * This event informs the event listener that user validation has
     * failed.
     * 
     * @param message A string containign an explaination of the
     * failure.
     */
    public void loginRejected(String message);

    /**
     * This event informs the event listener of another user of the
     * game. When logon first succeeds there will be one of these
     * callabcks sent for every currently logged-in user of this game.
     * As other users join, additional callabcks will be issued for
     * them.
     * 
     * @param userID The ID of the other user
     */
    public void userAdded(byte[] userID);

    /**
     * Informs the event listener that a user has logged out of the
     * game.
     * 
     * @param userID The user that logged out.
     */
    public void userDropped(byte[] userID);

    /**
     * Informs the event listener of the successful opening of a
     * channel.
     * 
     * @param name
     * @param channelID The ID of the newly joined channel
     */
    public void joinedChannel(String name, byte[] channelID);

    /**
     * Informs the user that they have left or been removed from a
     * channel.
     * 
     * @param channelID The ID of the channel left.
     */
    public void leftChannel(byte[] channelID);

    /**
     * Called whenever a new user joins a channel that we have open. A
     * set of these events are sent when we first join a channel-- one
     * for each pre-existing channel mamber. After that we get this
     * event whenever someone new joins the channel.
     * 
     * @param channelID the ID of the channel joined
     * @param userID the ID of the user who joined the channel
     */
    public void userJoinedChannel(byte[] channelID, byte[] userID);

    /**
     * Called whenever another user leaves a channel that we have open.
     * 
     * @param channelID the ID of the channel left
     * @param userID the ID of the user leaving the channel
     */
    public void userLeftChannel(byte[] channelID, byte[] userID);

    /**
     * This event informs the listener that data has arrived from the
     * SGS server channels.
     * 
     * @param chanID the ID of the channel on which the data was
     * received
     * @param from the ID of the sender of the data
     * @param data the data itself
     * @param reliable true if the data was sent reliably
     */
    public void recvdData(byte[] chanID, byte[] from, ByteBuffer data,
            boolean reliable);

    /**
     * TODO: description
     *
     * @param user
     */
    public void recvServerID(byte[] user);

    /**
     * Called whenever an attempted join/leave fails due to the target
     * channel being locked.
     * 
     * @param channelName the name of the channel.
     * @param userID the ID of the user attemping to join/leave
     */
    public void channelLocked(String channelName, byte[] userID);
}

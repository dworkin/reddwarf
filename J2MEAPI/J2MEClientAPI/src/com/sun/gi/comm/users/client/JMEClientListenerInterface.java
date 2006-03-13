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

package com.sun.gi.comm.users.client;

import com.sun.gi.utils.jme.ByteBuffer;
import com.sun.gi.utils.jme.Callback;

/**
 * SGS clients need to implement this interface to receive events from the
 * SGS server
 * @author as93050
 */
public interface JMEClientListenerInterface {
    
    /**
     * This event informs the event listener that further information is
     * needed in order to validate the user.
     * @param cbs An array of Callback structures to be filled out and sent back.
     */
    public void validationDataRequest(Callback[] cbs);
    /**
     * This event informs the event listener that the
     * validation has processed successfully and that the user can
     * start communicating with the back-end and other users.
     *
     * @param userID An ID issued to represent this user.  <b>UserIDs are not gauranteed
     * to remain the same between logons.</b>
     */
    public void loginAccepted(byte[] userID);
    
    /**
     * This event informs the event listener that user validation has failed.
     * @param message A string containing an explaination of the failure.
     */
    public void loginRejected(String message);
    
    /**
     * This event informs the event listener of another user of the game.
     * When logon first succeeds there will be one of these callabcks sent for every currently
     * logged-in user of this game.  As other users join, additional callabcks will be issued for them.
     * @param userID The ID of the other user
     */
    public void userLoggedIn(byte[] userID);
    
    /**
     * This event informs the event listener that a user has logged out of the game.
     * @param userID The user that logged out.
     */
    public void userLoggedOut(byte[] userID);
    
    /**
     * This event informs the event listener of the successful opening of a channel.
     * @param channelID The ID of the newly joined channel
     */
    public void joinedChannel(String name, byte[] channelID);
    
    /**
     * This callback informs the user that they have left or been removed from a channel.
     * @param channelID The ID of the channel left.
     */
    public void leftChannel(byte[] channelID);
    
    /**
     * This method is called whenever a new user joins a channel that we have open.
     * A set of these events are sent when we first join a channel-- one for each pre-existing
     * channel mamber.  After that we get thsi event whenever someone new joins the channel.
     * @param channelID The ID of the channel joined
     * @param userID The ID of the user who joined the channel
     */
    
    public void userJoinedChannel(byte[] channelID, byte[] userID);
    
    
    /**
     * This method is called whenever another user leaves a channel that we have open.
     * @param channelID The ID of the channel left
     * @param userID The ID of the user leaving the channel
     */
    public void userLeftChannel(byte[] channelID, byte[] userID);
    
    /**
     * This event informs the listener that data has arrived from the SGS server channels.
     * @param chanID The ID of the channel on which the data has been received
     * @param from The ID of the sender of the data
     * @param data The data itself
     */
    
    public void dataReceived(byte[] chanID, byte[] from, ByteBuffer data);
    
    /**
     * receive the server id
     * @param user
     */
    public void recvServerID(byte[] user);
    
    /**
     * Let the client know that we have discovered the games.
     */
    public void discoveredGames();
    
    /**
     * Let the client know about any exceptions that occurred when sending
     * or receiving data from the server 
     */
    public void exceptionOccurred(Exception ex);
    
    
    
    
    
    
        
}

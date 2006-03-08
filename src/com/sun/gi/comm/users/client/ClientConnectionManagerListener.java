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

import javax.security.auth.callback.Callback;

/**
 * ClientConnectionManagerListener handles callbacks from a
 * ClientConnectionManager.
 * 
 * @see ClientConnectionManager
 * 
 * @author Jeffrey P. Kesselman
 * @version 1.0
 */
public interface ClientConnectionManagerListener {

    public void validationRequest(Callback[] callbacks);

    public void connected(byte[] myID);

    public void connectionRefused(String message);

    public void failOverInProgress();

    public void reconnected();

    public void disconnected();

    /**
     * Called when a user joins the game. When a client initially
     * connects to the game it will receive a userJoined callback for
     * every other user rpesent. Aftre that every time a new user joins,
     * another callback will be issued.
     * 
     * @param userID The ID of the joining user.
     */
    public void userJoined(byte[] userID);

    /**
     * Called when a user leaves the game. This occurs either when a
     * user purposefully disconnects or when they drop and do not
     * re-connect within the timeout specified for the reconnection key
     * in the SGS backend.
     * <p>
     * <b>NOTE: In certain rare cases (such as the death of a slice),
     * notification may be delayed. (In the slice-death case it is
     * delayed until a watchdog notices the dead slice.)</b>
     * 
     * @param userID The ID of the user leaving the system.
     */
    public void userLeft(byte[] userID);

    /**
     * Called after sucessful completion of a channel open operation.
     * 
     * @param channel the channel object used to communicate on the
     * opened channel.
     */
    public void joinedChannel(ClientChannel channel);

    /**
     * Called whenever an attempted join/leave fails due to the target
     * channel being locked.
     * 
     * @param channelName the name of the channel
     * @param userID the ID of the user attemping to join/leave
     */
    public void channelLocked(String channelName, byte[] userID);
}

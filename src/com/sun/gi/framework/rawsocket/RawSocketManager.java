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

package com.sun.gi.framework.rawsocket;

import java.nio.ByteBuffer;

import com.sun.gi.logic.Simulation;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;

/**
 * A RawSocketManager manages creation and control of sockets connected
 * to arbitrary hosts in the "outside world". Once a
 * <code>RawSocketListener</code> initiates a connection via
 * openSocket, it implicitly becomes registered to receive data on that
 * socket until closeSocket is called.
 * </p>
 * 
 * @author Sten Anderson
 * @version 1.0
 * 
 */
public interface RawSocketManager {

    /**
     * <p>
     * Attempts to open a socket to the specified host on the specified
     * port. This will implicitly register the given
     * <code>RawSocketListener</code> for events on this socket.
     * </p>
     * 
     * <p>
     * The socket is not immediately opened, but rather queued to be
     * opened at a near future time. Once the connection process
     * completes a socketOpened event is generated.
     * </p>
     * 
     * @param socketID the ID of the socket.
     * @param sim the simulation.
     * @param access the type of access.
     * @param startObjectID the ID of the GLO.
     * @param host the host to connect to.
     * @param port the port to connect to.
     * @param reliable if true, the connection with be reliable.
     * 
     * @return a socketID that can be used in future communication to
     * reference the socket.
     */
    public long openSocket(long socketID, Simulation sim, ACCESS_TYPE access,
            long startObjectID, String host, int port, boolean reliable);

    /**
     * Attempts to send the given data over the socket associated with
     * the given socketID. The method will not return until the entire
     * buffer has been drained.
     * 
     * @param socketID used to lookup the associated socket.
     * @param data the data to send.
     * 
     * @return the number of bytes actually sent.
     */
    public long sendData(long socketID, ByteBuffer data);

    /**
     * <p>
     * Closes the socket associated with the specified socketID. This
     * implicitly deregisters the <code>RawSocketListener</code>
     * associated with this socketID.
     * </p>
     * 
     * <p>
     * Socket closure is not immediate, rather the socket is queued to
     * be closed. Multiple calls to this method with the same socketID
     * will be ignored.
     * </p>
     * 
     * @param socketID the ID of the socket to close.
     */
    public void closeSocket(long socketID);

    /**
     * Returns the next available socket ID.
     * 
     * @return the next socket ID.
     */
    public long getNextSocketID();

}

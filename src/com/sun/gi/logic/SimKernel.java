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

package com.sun.gi.logic;

import java.nio.ByteBuffer;

import com.sun.gi.framework.rawsocket.RawSocketManager;
import com.sun.gi.framework.timer.TimerManager;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.logic.impl.SimThreadImpl;

/**
 * SimKernel is the interface to the logic engine. Each game
 * (simulation) in a slice has its own run-time sim object that
 * implements this interface and provides the operating context for the
 * game.
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface SimKernel {

    public void addSimulation(Simulation sim);

    public void simHasNewTask();

    public void removeSimulation(Simulation sim);

    /**
     * @param impl
     */
    public void returnToThreadPool(SimThreadImpl impl);

    /**
     * @param timerManager
     */
    public void setTimerManager(TimerManager timerManager);

    /**
     * 
     * @param tid
     * @param access
     * @param sim
     * @param objID
     * @param delay
     * @param repeat
     * @return an identifier that refers to the timer event
     */
    public long registerTimerEvent(long tid, ACCESS_TYPE access,
            Simulation sim, long objID, long delay, boolean repeat);

    // Hooks into the RawSocketManager, added 1/16/2006

    /**
     * Sets the Raw Socket Manager.
     *
     * @param socketManager the RawSocketManager to set
     */
    public void setRawSocketManager(RawSocketManager socketManager);

    /**
     * Requests that a socket be opened at the given host on the given
     * port. The returned ID can be used for future communication with
     * the socket that will be opened. The socket ID will not be valid,
     * and therefore should not be used until the connection is
     * complete. Connection is complete once the
     * SimRawSocketListener.socketOpened() call back is called.
     * 
     * @param sim the simulation requesting the connection.
     * @param access the access type (GET, PEEK, or ATTEMPT)
     * @param objID a reference to the GLO initiating the connection.
     * @param host a String representation of the remote host.
     * @param port the remote port.
     * @param reliable if true, the connection will use a reliable
     * protocol.
     * 
     * @return an identifier that can be used for future communication
     * with the socket.
     */
    public long openSocket(long socketID, Simulation sim, ACCESS_TYPE access,
            long objID, String host, int port, boolean reliable);

    /**
     * Sends data on the socket mapped to the given socketID. This
     * method will not return until the entire buffer has been drained.
     * 
     * @param socketID the socket identifier
     * @param data the data to send. The buffer should be in a ready
     * state, i.e. flipped if necessary.
     * 
     * @return the number of bytes sent.
     */
    public long sendRawSocketData(long socketID, ByteBuffer data);

    /**
     * Requests that the socket matching the given socketID be closed.
     * The socket should not be assumed to be closed, however, until the
     * call back SimRawSocketListener.socketClosed() is called.
     * 
     * @param socketID the identifier of the socket
     */
    public void closeSocket(long socketID);

    /**
     * @return the next timer id.
     */
    public long getNextTimerID();

    /**
     * @return the next socket id.
     */
    public long getNextSocketID();
}

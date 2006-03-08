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

package com.sun.gi.apps.rawsocket;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.sun.gi.framework.rawsocket.SimRawSocketListener;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.SimTask;

/**
 * <p>
 * Title: SocketServicer
 * </p>
 * 
 * <p>
 * Description: A helper class to aid in testing the
 * <code>RawSocketManager</code>
 * </p>
 * 
 * @author Sten Anderson
 * @version 1.0
 */
public class SocketServicer
        implements GLO, SimRawSocketListener /* , SimTimerListener */
{
    private static final long serialVersionUID = 1L;

    private int curBufferSize = 1;

    /**
     * Called when the socket mapped to the specified socketID has been
     * successfully opened and ready for communication.
     * 
     * @param socketID the ID of the socket.
     */
    public void socketOpened(long socketID) {
        // System.out.println("SocketServicer: Socket ID " + socketID +
        // " is open for business!");
        // System.out.flush();

        /*
         * try { GLOReference thisRef = task.makeReference(this);
         * task.registerTimerEvent(ACCESS_TYPE.GET, 5000, true,
         * thisRef); } catch (InstantiationException ie) {
         * ie.printStackTrace(); }
         */

        writeBytes(socketID);

    }

    /*
     * public void timerEvent(SimTask task, long eventID) {
     * writeBytes(task, 0); }
     */

    public void writeBytes(long socketID) {
        SimTask task = SimTask.getCurrent();
        if (curBufferSize == 0 || curBufferSize >= 20) {
            System.out.println("Done sending data");
            task.closeSocket(socketID);
            curBufferSize = 1;
            return;
        }

        curBufferSize++;
        ByteBuffer buffer = ByteBuffer.allocate(curBufferSize);
        for (int i = 0; i < curBufferSize; i++) {
            buffer.put((byte) socketID); // easily identifies which
                                            // socket is writing
        }

        task.sendRawSocketData(socketID, buffer);

        System.out.println("Wrote bytes");
    }

    /**
     * Called when there is incoming data on the socket mapped to the
     * given ID.
     * 
     * @param socketID the ID of the socket.
     * @param data the incoming data.
     */
    public void dataReceived(final long socketID, ByteBuffer data) {
        System.out.println("RawSocketTestBoot Received on ID " + socketID
                + " data:" + new String(data.array()).trim());

        writeBytes(socketID);

    }

    /**
     * Called when the socket with the given ID is closed.
     * 
     * @param socketID the ID of the socket.
     */
    public void socketClosed(long socketID) {
        System.out.println("RawSocketTestBoot: SocketID " + socketID
                + " Closed.");
    }

    public void socketException(long socketID, IOException exception) {
        System.out.println("RawSocketTestBoot: Exception on SocketID "
                + socketID);
        exception.printStackTrace();
    }

}

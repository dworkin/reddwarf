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

package com.sun.gi.gamespy;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class JNITransport {
    private static List<TransportListener> listeners =
        new ArrayList<TransportListener>();

    public static void initialize() {
        System.err.println("init GameSpy");
        System.loadLibrary("GameSpyJNI");
        init();
    }

    public JNITransport() throws InstantiationException {
        throw new InstantiationException("Transport is a static interface to "
                + "native code and cannot be instantiated.");
    }

    public static void addListener(TransportListener l) {
        listeners.add(l);
    }

    // JNI stubs
    private static native void init();

    public static native void gt2Accept(long connHandle);

    public static native void gt2CloseAllConnections(long socketHandle);

    public static native void gt2CloseAllConnectionsHard(long socketHandle);

    public static native void gt2CloseConnection(long connHandle);

    public static native void gt2CloseConnectionHard(long connHandle);

    public static native void gt2CloseSocket(long socketHandle);

    public static native long gt2Connect(long socketHandle, String remoteAddr,
            byte[] message, int msgLength, int timeout);

    public static native long gt2CreateSocket(String localAddr, int outBuffSz,
            int inBuffSz);

    public static native void gt2Listen(long socketHandle);

    public static native void gt2Reject(long connectionHandle, byte[] message,
            int mesgLength);

    public static native void gt2Send(long connectionHandle, byte[] message,
            int msgLength, boolean reliable);

    public static native void gt2Think(long socketHandle);

    public static native long lastResult();

    // callabcks from JNI code

    public static void gt2SocketErrorCallback(long socketHandle) {
        fireSocketError(socketHandle);
    }

    public static void gt2ConnectedCallback(long connectionHandle, int result,
            byte[] message, int msgLength) {
        fireConnected(connectionHandle, result, message, msgLength);
    }

    public static void gt2ClosedCallback(long connectionHandle, int reason) {
        fireClosed(connectionHandle, reason);
    }

    public static void gt2PingCallback(long connectionHandle, int latency) {
        firePing(connectionHandle, latency);
    }

    public static void gt2ConnectAttemptCallback(long socketHandle,
            long connectionHandle, long ip, short port, int latency,
            byte[] message, int msgLength) {
        fireConnectAttempt(socketHandle, connectionHandle, ip, port, latency,
                message, msgLength);

    }

    // callback logic

    /**
     * fireSocketError
     * 
     * @param socketHandle long
     */
    private static void fireSocketError(long socketHandle) {
        for (Iterator i = listeners.iterator(); i.hasNext();) {
            ((TransportListener) i.next()).socketError(socketHandle);
        }
    }

    /**
     * fireConnected
     * 
     * @param connectionHandle long
     * @param result long
     * @param message byte[]
     * @param msgLength int
     */
    private static void fireConnected(long connectionHandle, long result,
            byte[] message, int msgLength) {
        for (TransportListener listener : listeners) {
            listener.connected(connectionHandle, result, message, msgLength);
        }
    }

    /**
     * fireClosed
     * 
     * @param connectionHandle long
     * @param reason long
     */
    private static void fireClosed(long connectionHandle, long reason) {
        for (TransportListener listener : listeners) {
            listener.closed(connectionHandle, reason);
        }
    }

    /**
     * firePing
     * 
     * @param connectionHandle long
     * @param latency int
     */
    private static void firePing(long connectionHandle, int latency) {
        for (TransportListener listener : listeners) {
            listener.ping(connectionHandle, latency);
        }
    }

    /**
     * fireConnectAttempt
     * 
     * @param socketHandle long
     * @param connectionHandle long
     * @param ip long
     * @param port short
     * @param latency int
     * @param message byte[]
     * @param msgLength int
     */
    private static void fireConnectAttempt(long socketHandle,
            long connectionHandle, long ip, short port, int latency,
            byte[] message, int msgLength)
    {
        for (TransportListener listener : listeners) {
            listener.connectAttempt(socketHandle, connectionHandle, ip, port,
                    latency, message, msgLength);
        }
    }
}

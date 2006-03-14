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

package com.sun.gi.framework.interconnect.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sun.gi.framework.interconnect.TransportChannel;
import com.sun.gi.framework.interconnect.TransportChannelListener;
import com.sun.gi.utils.SGSUUID;

public class LRMPTransportChannel implements TransportChannel {

    private String name;
    SGSUUID uuID;
    private LRMPTransportManager transportManager;
    private List<TransportChannelListener> listeners =
        new ArrayList<TransportChannelListener>();

    LRMPTransportChannel(String channelName, SGSUUID id,
            LRMPTransportManager mgr) {
        name = channelName;
        uuID = id;
        transportManager = mgr;
    }

    public void sendData(ByteBuffer data) throws IOException {
        transportManager.sendData(uuID, data);
    }

    public void addListener(TransportChannelListener l) {
        synchronized (listeners) {
            if (!listeners.contains(l)) {
                listeners.add(l);
            }
        }
    }

    /**
     * Removes the given listener if it exists. After the last listener
     * is removed, the channel will be closed at the TransportManager
     * level.
     * 
     * @param l the listener to remove
     */
    public void removeListener(TransportChannelListener l) {
        synchronized (listeners) {
            if (listeners.contains(l)) {
                listeners.remove(l);
            }
            if (listeners.isEmpty()) {
                transportManager.closeChannel(uuID);
            }
        }
    }

    public void closeChannel() {
        /**
         * @todo Implement this
         * com.sun.gi.framework.interconnect.TransportChannel method
         */
        throw new java.lang.UnsupportedOperationException(
                "Method closeChannel() not yet implemented.");
    }

    /**
     * doCloseChannel
     */
    public void doCloseChannel() {
        for (Iterator i = listeners.iterator(); i.hasNext();) {
            ((TransportChannelListener) i.next()).channelClosed();
        }
    }

    /**
     * doRecieveData
     * 
     * @param data ByteBuffer
     */
    public void doRecieveData(ByteBuffer data) {
        for (Iterator i = listeners.iterator(); i.hasNext();) {
            ((TransportChannelListener) i.next()).dataArrived(data);
        }

    }

    /**
     * getName
     * 
     * @return String
     */
    public String getName() {
        return name;
    }

    /**
     * sendData
     * 
     * @param byteBuffers ByteBuffer[]
     * @throws IOException 
     */
    public void sendData(ByteBuffer[] byteBuffers) throws IOException {
        transportManager.sendData(uuID, byteBuffers);
    }

    public void close() {
    // TODO Auto-generated method stub

    }

}

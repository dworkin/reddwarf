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


/*
 * LobbyManager.java
 *
 * Created by: seth proctor (sp76946)
 * Created on: Sun Feb 26, 2006	 9:51:41 PM
 * Desc: 
 *
 */

package com.sun.gi.apps.hack.client;

import com.sun.gi.comm.users.client.ClientConnectionManager;

import com.sun.gi.apps.hack.share.CharacterStats;

import java.nio.ByteBuffer;

import java.util.Collection;
import java.util.HashSet;


public class LobbyManager implements LobbyListener
{

    //
    private HashSet<LobbyListener> listeners;

    //
    private ClientConnectionManager connManager = null;

    /**
     *
     */
    public LobbyManager() {
        listeners = new HashSet<LobbyListener>();
    }

    /**
     *
     */
    public void setConnectionManager(ClientConnectionManager connManager) {
        if (this.connManager == null)
            this.connManager = connManager;
    }

    /**
     *
     */
    public void addLobbyListener(LobbyListener listener) {
        listeners.add(listener);
    }

    /**
     * FIXME: this needs to take some action info, but since there's only
     * one command to take right now, this is just a string
     */
    public void action(String gameName, String characterName) {
        // FIXME: for now there's only one command, so there aren't any
        // option here
        ByteBuffer bb = ByteBuffer.allocate(5 + gameName.length() +
                                            characterName.length());
        bb.put((byte)1);
        bb.putInt(gameName.length());
        bb.put(gameName.getBytes());
        bb.put(characterName.getBytes());
        connManager.sendToServer(bb, true);
    }

    /**
     *
     */
    public void gameAdded(String game) {
        for (LobbyListener listener : listeners)
            listener.gameAdded(game);
    }

    /**
     *
     */
    public void gameRemoved(String game) {
        for (LobbyListener listener : listeners)
            listener.gameRemoved(game);
    }

    /**
     *
     */
    public void playerCountUpdated(int count) {
        for (LobbyListener listener : listeners)
            listener.playerCountUpdated(count);
    }

    /**
     *
     */
    public void playerCountUpdated(String game, int count) {
        for (LobbyListener listener : listeners)
            listener.playerCountUpdated(game, count);
    }

    /**
     *
     */
    public void setCharacters(Collection<CharacterStats> characters) {
        for (LobbyListener listener : listeners)
            listener.setCharacters(characters);
    }

}

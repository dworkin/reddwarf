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

package com.sun.gi.apps.mcs.matchmaker.client;

/**
 * 
 * <p>Title: IGameChannelListener</p>
 * 
 * <p>Description: The IGameChannelListener signs up to receive the responses 
 * from a IGameChannel's command requests.</p>
 * 
 * @author	Sten Anderson
 * @version 1.0
 */
public interface IGameChannelListener {

    /**
     * Called when a player enters the game room.  This call back is fired once
     * for each user as they join the room.  It is also fired once for each player
     * already connected to the game room when a player first joins to receive the 
     * names of the players.
     * 
     * @param player		the user ID of the player.
     * @param name			the user name of the player
     */
	public void playerEntered(byte[] player, String name);

    /**
     * Fired once each time a player leaves the game room.
     * 
     * @param player			the ID of the player that left
     */
    public void playerLeft(byte[] player);

    
    /**
     * Called when a text message is received on the game room channel.
     * 
     * @param from			the user ID of the sender
     * @param text			the message
     * @param wasPrivate	if true, this user was the only recipient of the message
     */
    public void receiveText(byte[] from, String text, boolean wasPrivate);

    /**
     * Called to announce the ready state of the given player.  All players in
     * the game room must indicate their readiness before a game can be started.
     * 
     * @param player		the player
     * @param isReady		is true, the player is ready to start the game
     */
    public void playerReady(byte[] player, boolean isReady);

    /**
     * Called in response to IGameChannel.startGame() when the command fails.
     * 
     * @param reason		the reason for the failure
     */
    public void startGameFailed(String reason);

    /**
     * Called as confirmation to the IGameChannel.startGame() that the command
     * completely successfully.
     * 
     * @param game
     */
    public void gameStarted(GameDescriptor game);
}

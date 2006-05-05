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

package com.sun.gi.comm.routing;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import javax.security.auth.Subject;

import com.sun.gi.comm.users.server.SGSUser;
import com.sun.gi.framework.install.ChannelFilterRec;

/**
 * This interface defines the fundemental functionality of a Router.
 * Routers create and dispose of UserIds and move messages to users by
 * way of their user IDs.
 * 
 * <p>
 * Title: Router
 * </p>
 * <p>
 * Description: A tier 1 message router
 * </p>
 * 
 * @author Jeff Kesselman
 * @version 1.0
 */
public interface Router {

    /**
     * This call is made in order to allocate a new unqiue UserID.
     * 
     * @param user
     * @param subject
     * 
     * @throws IOException
     */
    public void registerUser(SGSUser user, Subject subject)
            throws InstantiationException, IOException;

    /**
     * This call is used to free a UserID that is no longer needed.
     * 
     * @param user The ID to dispose.
     */
    public void deregisterUser(SGSUser user);

    public SGSChannel openChannel(String channelName);

    public SGSChannel getChannel(ChannelID cid);

    public boolean validateReconnectKey(UserID uid, byte[] key);

    public void serverMessage(boolean reliable, UserID uid,
            ByteBuffer databuff);

    public void addRouterListener(RouterListener listener);

    /**
     * Joins the specified user to the Channel referenced by the given
     * ChannelID.
     * 
     * @param uid the user
     * @param cid the ChannelID
     */
    public void join(UserID uid, ChannelID cid);

    /**
     * Removes the specified user from the Channel referenced by the
     * given ChannelID.
     * 
     * @param uid the user
     * @param cid the ChannelID
     */
    public void leave(UserID uid, ChannelID cid);

    /**
     * Locks the given channel based on shouldLock. Users cannot
     * join/leave locked channels except by way of the Router.
     * 
     * @param cid the channel ID
     * @param shouldLock if true, will lock the channel, otherwise
     * unlock it.
     */
    public void lock(ChannelID cid, boolean shouldLock);

    /**
     * Closes the local view of the channel mapped to ChannelID. Any
     * remaining users will be notified as the channel is closing.
     * 
     * @param cid the ID of the channel to close.
     */
    public void closeChannel(ChannelID cid);
    
    /**
     * Sets the channel filters for this Router.  New channels created by this
     * Router will have new instances of the filters attached as specified by 
     * the given filter descriptors. 
     * 
     * @param filters		a List of ChannelFilters descriptors that specify 
     * 						the filters to attach to new channels created
     * 						by this Router.
     */
    public void setChannelFilters(List<ChannelFilterRec> filters);
    
    /**
     * Initiates shutting down the Router.  In general this includes 
     * disconnecting all users and closing channels.
     */
    public void shutdown();
}

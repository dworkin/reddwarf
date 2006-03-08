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

package com.sun.gi.apps.chattest;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.security.auth.Subject;

import com.sun.gi.comm.routing.ChannelID;
import com.sun.gi.comm.routing.UserID;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimUserDataListener;
import com.sun.gi.logic.SimUserListener;

public class ChatTestBoot
        implements SimBoot<ChatTestBoot>, SimUserListener, SimUserDataListener
{
    private static final long serialVersionUID = 1L;

    UserID myUserID = null;
    List<UserID> users = new ArrayList<UserID>();

    ChannelID echoID;
    ChannelID noJoinID;

    GLOReference<? extends ChatTestBoot> thisobj;

    public void boot(GLOReference<? extends ChatTestBoot> thisGLO,
            boolean firstBoot)
    {
        SimTask task = SimTask.getCurrent();
        System.err.println("Booting comm test, appid = " + task.getAppID());
        thisobj = thisGLO;
        task.addUserListener(thisobj);
        echoID = task.openChannel("echo");

        noJoinID = task.openChannel("noJoin");
        task.lock(noJoinID, true);

    }

    public void userLeft(UserID uid) {
        System.err.println("User left server: " + uid);
        users.remove(uid);
    }

    public void userJoined(UserID uid, Subject subject) {
        System.err.print("User Joined server: " + uid + " ( ");
        for (Object cred : subject.getPublicCredentials()) {
            System.err.print(cred + " ");
        }
        System.err.println(")");
        users.add(uid);
        SimTask.getCurrent().addUserDataListener(uid, thisobj);
    }

    public void userDataReceived(UserID from, ByteBuffer data) {
        byte[] inbytes = new byte[data.remaining()];
        data.get(inbytes);
        System.err.println("Data from user " + from + ": "
                + new String(inbytes));

        // Sten - new router feature test
        // task.leave(from, echoID);
        // task.join(from, noJoinID);
        // task.lock(noJoinID, false);
    }

    public void userJoinedChannel(ChannelID cid, UserID uid) {
        System.err.println("User " + cid + " joined channel " + uid);
        SimTask.getCurrent().setEvesdroppingEnabled(uid, echoID, true);
        SimTask.getCurrent().setEvesdroppingEnabled(uid, noJoinID, true);

        // test for forcabley closing a channel
        // task.closeChannel(cid);

        // test lock from leaving.
        /*
         * if (cid.equals(noJoinID)) { System.err.println("Locking
         * noJoin"); task.lock(noJoinID, true); }
         */
    }

    public void userLeftChannel(ChannelID cid, UserID uid) {
        System.err.println("User " + cid + " left channel " + uid);

    }

    public void dataArrived(ChannelID cid, UserID from, ByteBuffer buff) {
        // no-op
    }

    public void dataArrivedFromChannel(ChannelID cid, UserID from,
            ByteBuffer buff) {
        SimTask task = SimTask.getCurrent();
        task.sendData(cid, new UserID[] { from }, buff, true);
    }
}

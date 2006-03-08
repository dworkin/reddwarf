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

import java.util.ArrayList;
import java.util.List;
//import java.util.Random;

import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimBoot;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;

/**
 * RawSocketTestBoot is a test harness for the
 * <code>RawSocketManager</code>
 * 
 * @author Sten Anderson
 * @version 1.0
 */
public class RawSocketTestBoot implements SimBoot<RawSocketTestBoot> {

    private static final long serialVersionUID = 1L;

    List<GLOReference<SocketServicer>> servicerList;

    /**
     * Called multiple times. On firstBoot == true create all service
     * objects (which implement the SocketListener interface) and call
     * createSO() and store the references in a list in the boot object.
     * 
     * On subsequent calls when firstBoot == false, don't createSO(),
     * but the list of GLOReferences will automatically have the IDs
     * from the database.
     * 
     * Connect one socket per service object.
     * 
     */
    public void boot(GLOReference<? extends RawSocketTestBoot> bootGLO,
            boolean firstBoot)
    {
        SimTask task = SimTask.getCurrent();
        if (firstBoot) {
            int numServicers = 5;

            System.out.println("RawSocketTestBoot: firstBoot");
            // new Throwable().printStackTrace();
            servicerList = new ArrayList<GLOReference<SocketServicer>>();

            for (int i = 0; i < numServicers; i++) {
                GLOReference<SocketServicer> ssRef =
                    task.createGLO(new SocketServicer());
                servicerList.add(ssRef);
            }
        }

        String host = "localhost";
        // String host = "192.168.0.5";

        System.out.println("RawSocketTestBoot: Socket ID = "
                + task.openSocket(ACCESS_TYPE.GET, servicerList.get(0), host,
                        6000, false));

        System.out.println("RawSocketTestBoot: Socket ID = "
                + task.openSocket(ACCESS_TYPE.GET, servicerList.get(1), host,
                        6000, false));

        /*
         * for (int i = 0; i < servicerList.size(); i++) { GLOReference
         * ref = servicerList.get(i);
         * System.out.println("RawSocketTestBoot: Socket ID = " +
         * task.openSocket(ACCESS_TYPE.GET, ref, host, getPort(),
         * true));
         *  }
         */
    }

    /*
    private int getPort() {
        return new Random().nextInt(10) + 5000;
    }
    */
}

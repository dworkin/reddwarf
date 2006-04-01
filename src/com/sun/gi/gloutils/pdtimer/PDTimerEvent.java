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

package com.sun.gi.gloutils.pdtimer;

import java.lang.reflect.Method;
import java.util.logging.Logger;

import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;

/**
 * This class represents an event to be triggered by the PDTimer.
 * All the methods are for use by PDTimer.  As far as the app is
 * concerned this is an opaque class. 
 * @author Jeff Kesselman
 * @version 1.0
 */
public class PDTimerEvent implements GLO {

    private static final long serialVersionUID = 1L;
    private static Logger log = Logger.getLogger("com.sun.gi.gloutils.pdtimer");

    boolean isActive = true;
    ACCESS_TYPE accessType;
    long delay;
    boolean repeat;
    GLOReference target;
    String methodName;
    Object[] parameters;

    /**
     * @param access
     * @param delay
     * @param repeat
     * @param target
     * @param methodName
     * @param parameters
     */
    PDTimerEvent(ACCESS_TYPE access, long delay, boolean repeat,
            GLOReference target, String methodName, Object[] parameters) {
        // TODO Auto-generated constructor stub
        this.accessType = access;
        this.delay = delay;
        this.repeat = repeat;
        this.target = target;
        this.methodName = methodName;
        this.parameters = parameters;
    }

    /**
     * @param task
     */
    void fire(SimTask task) {
        if (!isActive) {
            System.err.println("ERROR: Nonactive timer event fired!");
            return;
        }
        Object targetObject = target.peek(task);
        Class[] classes = new Class[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            classes[i] = parameters[i].getClass();
        }
        try {
            Method method =
                targetObject.getClass().getMethod(methodName, classes);
            task.queueTask(accessType, target, method, parameters);
            isActive = false;
        } catch (SecurityException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

    }

    long delayTime() {
        return delay;
    }

    boolean requiresCleanup() {
        return !isActive;
    }

    boolean isRepeating() {
        return repeat;
    }

    void reset(SimTask task) {
        task.access_check(ACCESS_TYPE.GET, this);
        log.finest("Restting event");
        isActive = true;
    }

    boolean isMoribund() {
        return (!isActive) && (!repeat);
    }

    boolean isActive() {
        return isActive;
    }
}

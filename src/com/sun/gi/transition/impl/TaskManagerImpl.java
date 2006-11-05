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

package com.sun.gi.transition.impl;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.gi.objectstore.DeadlockException;
import com.sun.gi.objectstore.NonExistantObjectIDException;
import com.sun.gi.objectstore.ObjectStore;
import com.sun.gi.objectstore.Transaction;
import com.sun.gi.transition.PeriodicTaskHandle;
import com.sun.gi.transition.Task;
import com.sun.gi.transition.TaskManager;
import com.sun.gi.logic.GLO;
import com.sun.gi.logic.GLOReference;
import com.sun.gi.logic.impl.GLOReferenceImpl;
import com.sun.gi.logic.impl.SimulationImpl;
import com.sun.gi.logic.SimTask;
import com.sun.gi.logic.SimTask.ACCESS_TYPE;
import com.sun.gi.logic.SimTimerListener;

public class TaskManagerImpl implements TaskManager {
    private static final String TASKLISTNAME = "_SGS_TaskManagerData";
    private static Logger log = Logger.getLogger("com.sun.gi.transition");
    
    // Not persisted, so doesn't need to use GLORefs
    private final Map<PeriodicTaskWrapper, Long> periodicTimerMap;

    private final Method callbackMethod;
    
    private GLOReference<TaskList> taskListRef;
    
    public TaskManagerImpl() {
        periodicTimerMap = new HashMap<PeriodicTaskWrapper, Long>();
        try {
            callbackMethod =
                    SimTimerListener.class.getMethod("timerEvent",
                    new Class[] { Long.TYPE });
        } catch (NoSuchMethodException ex) {
            NoSuchMethodError err = new NoSuchMethodError();
            err.initCause(ex);
            throw err;
        }
    }
    
    public void restartTasks(SimulationImpl sim, ObjectStore ostore) {
        // Read the persistent task list and reschedule timers for
        // all the tasks we find there (since they were running when we
        // crashed).
        synchronized(ostore){
            Transaction trans =
                    ostore.newTransaction(this.getClass().getClassLoader());
            trans.start();
            try {
                long taskListID = trans.lookup(TASKLISTNAME);
                if (taskListID == ObjectStore.INVALID_ID) {
                    taskListID = trans.create(new TaskList(), TASKLISTNAME);
                }
                taskListRef = new GLOReferenceImpl<TaskList>(taskListID);
                TaskList taskList = (TaskList) trans.peek(taskListID);
                //System.err.println("TaskList = " + taskList);
                trans.commit();
                
                Method restartTasksMethod =
                    TaskList.class.getMethod("restartTasks", new Class[] {});
                sim.queueTask(
                    sim.newTask(taskListID,
                        restartTasksMethod,
                        new Object[] {},
                        null));
            } catch (DeadlockException e) {
                e.printStackTrace();
                trans.abort();
            } catch (NonExistantObjectIDException e) {
                e.printStackTrace();
                trans.abort();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
    }
    
    public void persistTask(GLOReference ref) {
        SimTask simTask = SimTask.getCurrent();
        TaskList taskList = taskListRef.get(simTask);
        taskList.add(ref);
    }

    public void depersistTask(GLOReference ref) {
        SimTask simTask = SimTask.getCurrent();
        TaskList taskList = taskListRef.get(simTask);
        taskList.remove(ref);
    }

    public void scheduleTask(Task task) {
        SimTask simTask = SimTask.getCurrent();
        
        GLOReference<TaskWrapper> taskWrapperRef =
                TaskWrapper.wrapTask(task, true);
        
        persistTask(taskWrapperRef);
        
        // Queue the TimerWrapper with access GET since it will need
        // to be locked anyway for deletion.
        simTask.queueTask(SimTask.ACCESS_TYPE.GET,
                taskWrapperRef,
                callbackMethod,
                new Object[] { new Long(-1) });
    }

    public void scheduleTask(Task task, long delay) {
        SimTask simTask = SimTask.getCurrent();
        
        GLOReference<TaskWrapper> taskWrapperRef =
                TaskWrapper.wrapTask(task, true);

        persistTask(taskWrapperRef);

        // Queue the TimerWrapper with access GET since it will need
        // to be locked anyway for deletion.
        simTask.registerTimerEvent(SimTask.ACCESS_TYPE.GET,
                delay,
                false,
                taskWrapperRef);
    }

    public PeriodicTaskHandle schedulePeriodicTask(Task task,
            long delay,
            long period)
    {
        SimTask simTask = SimTask.getCurrent();
        GLOReference<PeriodicTaskWrapper> wrapperRef = 
                PeriodicTaskWrapper.create(task, period);
        PeriodicTaskWrapper wrapper = wrapperRef.get(simTask);

        persistTask(wrapperRef);
        
        // We can register with access PEEK here, since the
        // PeriodicTaskWrapper won't need to be modified.
        Long timerID =
                simTask.registerTimerEvent(SimTask.ACCESS_TYPE.PEEK,
                    delay,
                    false,
                    wrapperRef);

        log.log(Level.FINER, 
                "Register timer for {0} ID {1}",
                new Object[] { wrapper, timerID });

        periodicTimerMap.put(wrapper, timerID);
        return new PeriodicTaskHandleImpl(wrapperRef);
    }
    
    public void setPeriodicTaskTimerID(PeriodicTaskWrapper wrapper,
            long newTimerID)
    {
        log.log(Level.FINEST, 
                "Update timer for {0} ID {1}",
                new Object[] { wrapper, newTimerID });

        periodicTimerMap.put(wrapper, newTimerID);
    }

    public void removePeriodicTask(PeriodicTaskWrapper wrapper) {
        SimTask simTask = SimTask.getCurrent();
        
        log.log(Level.FINER, "Cancel timer {0}", wrapper);
        
        // Remove this PeriodicTaskWrapper from the persistent
        // task list.
        depersistTask(simTask.lookupReferenceFor(wrapper));

        // @@ NOTE @@
        // We cannot remove this wrapper's entry from the periodicTimerMap,
        // because this transaction might fail and we may be asked for its
        // timerID in a future (successful) transaction.
        // TODO: this leaks map entries, but we can garbage collect them if
        // we add appropriate calls to the DeferredDeleteTimer command.
        Long timerID = periodicTimerMap.get(wrapper);
        if (timerID != null) {
            simTask.deregisterTimerEvent(timerID);
        } else {
            log.log(Level.WARNING, "No such periodic task {0}", wrapper);
        }
    }
}

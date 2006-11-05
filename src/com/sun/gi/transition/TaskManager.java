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

package com.sun.gi.transition;

import java.io.Serializable;

/**
 * Provides facilities for scheduling tasks.  Each task is a serializable
 * object that can be scheduled to be run now, at some time in the future, or
 * periodically. For the methods that run at some delayed point in the
 * future, the delay is taken from the moment when the scheduling method
 * was called.
 * <p>
 * For all methods on <code>TaskManager</code>, if the instance of
 * <code>Task</code> provided does not implement <code>ManagedObject</code>
 * then the <code>TaskManager</code> will persist the <code>Task</code>
 * until it finishes. This provides durability, and is particularly
 * convenient for simple tasks that the developer doesn't wish to manage
 * and remove manually. However, if the <code>Task</code> does implement
 * <code>ManagedObject</code>, then it's assumed that the <code>Task</code>
 * is already managed, and it is up to the developer to remove it from
 * the <code>DataManager</code> when finished.
 */
public interface TaskManager {

    /**
     * Schedules a task to run now.  The <code>TaskManager</code> will call the
     * task's {@link Task#run run} method as soon as possible after the
     * completion of the task in which this method is called, according to its
     * scheduling algorithm.  <p>
     *
     * If the call to the <code>run</code> method throws an exception, that
     * exception implements {@link ExceptionRetryStatus}, and its {@link
     * ExceptionRetryStatus#shouldRetry shouldRetry} method returns
     * <code>true</code>, then the <code>TaskManager</code> will make further
     * attempts to run the task.  It will continue those attempts until either
     * an attempt succeeds or it notices an exception is thrown that is not
     * retryable.  The <code>TaskManager</code> is permitted to treat a
     * non-retryable exception as a hint.  In particular, a task that throws a
     * non-retryable exception may be retried if the node running the task
     * crashes.
     *
     * @param	task the task to run
     * @throws	IllegalArgumentException if <code>task</code> does not
     *		implement {@link Serializable}
     * @throws	TaskRejectedException if the <code>TaskManager</code> refuses
     *		to accept the task because of resource limitations
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    void scheduleTask(Task task);

    /**
     * Schedules a task to run after a delay.  The <code>TaskManager</code>
     * will wait for the specified number of milliseconds, and then call the
     * task's {@link Task#run run} method as soon as possible after the
     * completion of the task in which this method is called, according to its
     * scheduling algorithm. <p>
     *
     * If the call to the <code>run</code> method throws an exception, that
     * exception implements {@link ExceptionRetryStatus}, and its {@link
     * ExceptionRetryStatus#shouldRetry shouldRetry} method returns
     * <code>true</code>, then the <code>TaskManager</code> will make further
     * attempts to run the task.  It will continue those attempts until either
     * an attempt succeeds or it notices an exception is thrown that is not
     * retryable.  The <code>TaskManager</code> is permitted to treat a
     * non-retryable exception as a hint.  In particular, a task that throws a
     * non-retryable exception may be retried if the node running the task
     * crashes.
     *
     * @param	task the task to run
     * @param	delay the number of milliseconds to delay before running the
     *		task
     * @throws	IllegalArgumentException if <code>task</code> does not
     *		implement {@link Serializable}, or if delay is less than
     *		<code>0</code>
     * @throws	TaskRejectedException if the <code>TaskManager</code> refuses
     *		to accept the task because of resource limitations
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    void scheduleTask(Task task, long delay);

    /**
     * Schedules a task to run periodically after a delay.  The
     * <code>TaskManager</code> will wait for the specified number of
     * milliseconds, and then call the task's {@link Task#run run} method as
     * soon as possible after the completion of the task in which this method
     * is called, according to its scheduling algorithm.  It will also arrange
     * to run the task periodically at the specified interval following the
     * delay until the {@link PeriodicTaskHandle#cancel
     * PeriodicTaskHandle.cancel} method is called on the associated handle.
     * At the start of each period, which occurs <code>period</code>
     * milliseconds after the scheduled start of the previous period, a new
     * task will be scheduled to run. The <code>TaskManager</code> will make
     * a best effort to run a new task in each period, but even if the task
     * cannot be run in one period, a new task will always be scheduled for
     * the following period. The <code>TaskManager</code> will wait until
     * the current attempt to run the task has ended before making another
     * attempt to run it, regardless of whether the attempts are for the same
     * or different periods.
     * <p>
     * If the call to the <code>run</code> method throws an exception, that
     * exception implements {@link ExceptionRetryStatus}, and its {@link
     * ExceptionRetryStatus#shouldRetry shouldRetry} method returns
     * <code>true</code>, then the <code>TaskManager</code> will make further
     * attempts to run the task.  It will continue those attempts until either
     * an attempt succeeds or it notices an exception is thrown that is not
     * retryable.  Note that calls to <code>PeriodicTaskHandle.cancel</code>
     * have no effect on attempts to retry a task after the first attempt.  The
     * <code>TaskManager</code> is permitted to treat a non-retryable exception
     * as a hint.  In particular, a task that throws a non-retryable exception
     * may be retried if the node running the task crashes.
     *
     * @param	task the task to run
     * @param	delay the number of milliseconds to delay before running the
     *		task
     * @param	period the number of milliseconds that should elapse between
     *		the starts of periodic attempts to run the task
     * @return	a handle for managing the scheduling of the task
     * @throws	IllegalArgumentException if <code>task</code> does not
     *		implement {@link Serializable}, if delay is less than
     *		<code>0</code>, or if period is less than <code>0</code>
     * @throws	TaskRejectedException if the <code>TaskManager</code> refuses
     *		to accept the task because of resource limitations
     * @throws	TransactionException if the operation failed because of a
     *		problem with the current transaction
     */
    PeriodicTaskHandle schedulePeriodicTask(Task task, long delay,
                                            long period);
}

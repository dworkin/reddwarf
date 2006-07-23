
package com.sun.sgs.manager.impl;

import com.sun.sgs.ManagedReference;
import com.sun.sgs.TimerHandle;
import com.sun.sgs.TimerListener;

import com.sun.sgs.kernel.TransactionProxy;

import com.sun.sgs.manager.TimerManager;

import com.sun.sgs.service.Transaction;


/**
 * This is a simple implementation of <code>TimerManager</code> that is the
 * default used.
 *
 * @since 1.0
 * @author James Megquier
 * @author Seth Proctor
 */
public class SimpleTimerManager extends TimerManager
{

    // the proxy used to access transaction state
    private TransactionProxy transactionProxy;

    /**
     * Creates an instance of <code>SimpleTimerManager</code>.
     *
     * @param transactionProxy the proxy used to access transaction state
     */
    public SimpleTimerManager(TransactionProxy transactionProxy) {
        super();

        this.transactionProxy = transactionProxy;
    }

    /**
     * Registers an event to be run at a given time.
     *
     * @param delay the time in milliseconds to wait before starting
     * @param repeat whether to repeat the event each <code>delay</code>
     *               milliseconds
     * @param reference the listener to call to start the event
     */
    public TimerHandle registerTimerEvent(long delay, boolean repeat,
            ManagedReference<? extends TimerListener> reference) {
        Transaction txn = transactionProxy.getCurrentTransaction();
        return txn.getTimerService().
            registerTimerEvent(txn, delay, repeat, reference);
    }

}

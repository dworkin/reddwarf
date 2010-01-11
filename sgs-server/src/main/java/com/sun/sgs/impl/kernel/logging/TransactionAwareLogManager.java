/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * --
 */

package com.sun.sgs.impl.kernel.logging;

import com.sun.sgs.impl.kernel.StandardProperties;

import com.sun.sgs.service.TransactionProxy;

import java.util.ArrayDeque;
import java.util.Properties;
import java.util.Queue;

import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.LogManager;

/**
 * A {@code LogManager} class that adds transactional semantics to {@code
 * Logger} instances in the application's namespace.  This class will either
 * infer the application's name space from the package used by the main {@code
 * AppListener}, or it can be specified manually by the following property:
 *
 * <p><dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <b>
 *	{@code com.sun.sgs.logging.app.namespace}
 *	</b><br>
 *	<i>Default:</i> the package named used in the {@code
 *	com.sun.sgs.app.listener} property.
 *
 * <dd style="padding-top: .5em">This property specifies the root of the
 * application namespace.  All loggers under this root will be have
 * transactional-semantics.  If this property is left unset the system will use
 * the namespace specified in the {@code com.sun.sgs.app.listener} property
 * value. <p>
 *
 * </dl>
 *
 * In order to load this class as the default {@code LogManager}, applications
 * must set the {@code java.util.logging.manager} system property to {@code
 * com.sun.sgs.impl.kernel.logging.TransactionAwareLogManager} prior to JVM
 * start up.
 *
 * <p>
 *
 * All {@code Logger} instances outside of the application's name space will
 * retain their default, non-transactional semantics.
 *
 * @see TransactionalHandler
 */
public final class TransactionAwareLogManager extends LogManager {

    /**
     * The namespace prefix used by all the properties for this class.
     */
    private static final String PROPERTIES_PREFIX = "com.sun.sgs.logging";

    /**
     * The property specified in the system properties for denoting the root
     * namespace of the application.
     */
    private static final String APP_NAMESPACE_PROPERTY =
	PROPERTIES_PREFIX + ".app.namespace";

    /**
     * The {@code TransactionProxy} used by the {@link TransactionalHandler}
     * handlers.
     */
    private TransactionProxy txnProxy;

    /**
     * The listing of {@code TransactionalLogger} instances that have yet to be
     * configured with a {@link TransactionProxy}, but were created prior to
     * this {@code TransactionAwareLogManager} being configured.  Note that not
     * all of these instances will end up being transactional.  However, due to
     * the order in which the {@code LogManager} is created in the JVM, some
     * {@code Logger} instances will be created prior to this component being
     * configured.  For this reason, we keep this list to later reconfigure
     * them when the {@code TransactionProxy} is available.
     */
    private final Queue<TransactionalLogger> unconfiguredLoggers;

    /**
     * The namespace of the application, which is used to determine which
     * {@code Logger} instances should be transactional.  Any namespace under
     * this will have transactional semantics.
     */
    private String appNamespace;

    /**
     * Default constructor used by the JVM at startup.
     */
    public TransactionAwareLogManager() {
	super();
	unconfiguredLoggers = new ArrayDeque<TransactionalLogger>();
	appNamespace = null;
    }

    /**
     * Configures this {@code LogManager} with the provided properties and uses
     * the {@code TransactionProxy} to add transactional semantics to any of
     * the specified application {@code Logger} instances.
     *
     * <p>
     *
     * Note that prior to this call, the application's namespace is not known
     * so any {@code Logger} instances will be of type {@code
     * TransactionalLogger}, but will be configured as non-transactional.
     * After this call, any of these instances that were in the application's
     * namespace will be transactional.
     *
     * @param properties the properties for configuring this component
     * @param txnProxy the transaction proxy     
     */
    public synchronized void configure(Properties properties,
				       TransactionProxy txnProxy) {
	this.txnProxy = txnProxy;

	String appListener = 
	    properties.getProperty(StandardProperties.APP_LISTENER);

	int lastDotBeforeClass = appListener.lastIndexOf(".");

	// Check to ensure that the main class has a package
	if (lastDotBeforeClass < 0) {
	    // in the event that the main class has no package, set the
	    // position of the now phantom dot to the length of the app
	    // Listener's name, which ensures that any loggers created based on
	    // the class's name will be transactional
	    lastDotBeforeClass = appListener.length();
	}

	String defaultAppNamespace = 
	    appListener.substring(0, lastDotBeforeClass);

	// if the applicate does not specify a specific namespace, we use the
	// namespace provided by the application listener.
	appNamespace = properties.getProperty(APP_NAMESPACE_PROPERTY,
					      defaultAppNamespace);

	// Now that the namespace is known, check any non-transaction
	// TransactionalLoggers that were created prior to this LogManager
	// being configured.  This could have happened if the Loggers were
	// created statically when the application's classes were loaded.  If
	// any of these Loggers are in the application's namespace, have them
	// configured to be transactional.
	for (TransactionalLogger lgr : unconfiguredLoggers) {
	    
	    // This list will likely include any of the servers loggers that
	    // were created statically, so we test to see whether the Logger
	    // belongs to the app's namespace before configuring its handlers
	    if (lgr.getName().startsWith(appNamespace)) {
		lgr.configure(txnProxy);
		lgr.config("This logger now has transactional semantics");
	    }
	}
	unconfiguredLoggers.clear();
    }

    /**
     * Returns the existing {@code Logger} for the provided name or creates a
     * new instance if none is found.  Note that <i>unlike the default
     * <tt>LogManager</tt> implementation</i>, this call will create a new
     * {@code Logger} instance.
     *
     * @param name the name of the logger
     *
     * @return the existing {@code Logger} for the provided name, or a new
     *         instance that was created by this call.
     */
    // NOTE: we rely on the fact that Logger.getLogger() will in turn call
    // LogManager.demandLogger().  LogManager.demandLogger() first checks to
    // see if a Logger has already been created by calling
    // LogManager.getLogger() and checking the results.  In order to subvert
    // the default LogManager behavior, we override that method to create a
    // new, possibly transaction-aware Logger instance.

    public synchronized Logger getLogger(String name) {
	// The root logger will have a 0-length name, in which case we should
	// return null and let the default LogManager code create the
	// RootLogger instance correctly.
	if (name == null || name.length() == 0) {
	    return null;
        }
	
	Logger result = super.getLogger(name);

	if (result == null) {	    
            boolean configured = (txnProxy != null);
	    // if no current Logger associated with that name, create the
	    // appropriate type of Logger.  If the transactionProxy is null, we
	    // haven't been configured yet, so create a TransactionalLogger but
	    // mark it non-transactional.
	    //
	    // If we have been configured, see if the requested Logger's name 
            // is in the application's namespace.
	    result = (!configured || name.startsWith(appNamespace)) 
		? new TransactionalLogger(name, null, txnProxy)
		: new SimpleLogger(name, null);

	    // there is a chance that an application may demand a Logger prior
	    // to the TxnAwareLogManager being configured with the
	    // TransactionProxy and the application's namespace.  Therefore, we
	    // add any such Loggers to a list of unconfigured ones and revisit
	    // them upon the manager's configuration.
	    if (!configured && (result instanceof TransactionalLogger)) {
                unconfiguredLoggers.add((TransactionalLogger) result);
            } else {
		result.config("This logger now has transactional semantics");
	    }

	    // this call is necessary to install all the handlers associated
	    // with the Logger.
	    addLogger(result);
	}
	return result;
    }

    /**
     * A utility class that exposes the {@code protected} constructor of the
     * {@code Logger} class.  We need this class so that we can create new
     * {@code Logger} instances in the {@link
     * TransactionAwareLogManager#getLogger(String)} method.
     */
    private static final class SimpleLogger extends Logger {

	public SimpleLogger(String name, String resourceBundleName) {
	    super(name, resourceBundleName);
	}
    }

    /**
     * A {@code Logger} class that provides optional transactional semantics.
     * Instances of this class will provide transaction semantics if they are
     * constructed with a valid, non-{@code null} instance of a {@link
     * TransactionProxy}, or if they are configured after construction with a
     * valid, non-{@code null} instance of a {@code TransactionProxy}.
     *
     * <p>
     *
     * This class does not interact with the transactions directly but instead
     * relies on {@link TransactionalHandler} instances to do so.  Adding a
     * {@link Handler} at run-time will function as expected and will not
     * change the output semantics.
     */
    private static final class TransactionalLogger extends Logger {

	/**
	 * The proxy that allows any {@link TransactionalHandler} associated
	 * with this {@code Logger} to join the current transaction.  Note that
	 * if this {@code Logger} was created before the {@link
	 * TransactionAwareLogManager#configure(Properties,TransactionProxy}}
	 * has been called, this will be {@code null}.
	 */
	private TransactionProxy txnProxy;

	/**
	 * Constructs a {@code TransactionalLogger} that will have
	 * transactional semantics if {@code txnProxy} is valid and non-{@code
	 * null}.
	 *
	 * @param name the name of this logger
	 * @param resourceBundleName the name of a {@link ResourceBundle} to be
	 *                           used for localizing messages for this
	 *                           logger.  May be {@code null} if none of
	 *                           the messages require localization.
	 * @param txnProxy the {@code TransactionProxy} used to join the
	 *                 current transaction when a report is logged.
	 */
	public TransactionalLogger(String name,
				   String resourceBundleName,
				   TransactionProxy txnProxy) {
	    super(name, resourceBundleName);
	    this.txnProxy = txnProxy;
	}
				      
	/**
	 * {@inheritDoc}
	 *
	 * If the provided {@code Handler} is an instance of {@link
	 * TransactionalHander} it will be added immediately.  Otherwise, the
	 * provided {@code Handler} will be wrapped by a {@code
	 * TransactionalHandler} and that handler will be added instead.
	 */
	public void addHandler(Handler handler) {
	    if (!(handler instanceof TransactionalHandler)) {
		// in the event that this Logger was created prior to the
		// LogManager being configured, the TransactionProxy will be
		// null.  In this case, we add the handler as normal and then
		// wait for the configure() call to wrap it in a
		// TransactionalHandler
		if (txnProxy == null) {
		    super.addHandler(handler);
                } else {
		    // wrap the original handler in one that has transactional
		    // semantics
		    super.addHandler(new TransactionalHandler(txnProxy, 
							      handler));
		}
            } else {
		// if we were passed an existing TransactionalHandler, use it
		// as is.  This case could occur if the handler had already
		// been created for another Logger.
		super.addHandler(handler);
	    }
	}


	/**
	 * Searches the logger parent hierarchy of the provided {@code Logger}
	 * until a parent is found who has {@code Handler} instances, and then
	 * adds those handlers to the provided logger.  Note that this method
	 * also modifies the logger to not use its parent handlers in order to
	 * prevent duplicate log entries.
	 */
	private void attachParentHandlers() {
	    Logger parent = this;
	    do {
		parent = parent.getParent();
	    } while (parent != null && parent.getHandlers().length == 0);
	    
	    if (parent == null) {
		// NOTE: this case should never happen since we would
		// eventually hit the LogManager$RootLogger which by default
		// has a Handler.  However a developer could feasibly adjust
		// some settings so that the RootLogger had no handler and none
		// of the child Loggers did as well.
		return;
	    }
	    
	    // Add all of the parent handlers to this Logger so that we can
	    // later wrap it.
	    Handler[] arr = parent.getHandlers();
	    for (Handler h : arr) {
		addHandler(h);
	    }
	    
	    // Now that we are using the same handler as the parent, avoid
	    // propagating the call to the parent Logger as this will result in
	    // duplicate log entries to the handler.
	    setUseParentHandlers(false);
	}		

	/**
	 * Configures this {@code Logger} with the provided {@code
	 * TransactionProxy} so that all messages logged will have transaction
	 * semantics.  This method is used for reconfiguring loggers that were
	 * created prior to the {@link TransactionAwareLogManager} being
	 * configured.
	 *
	 * @param txnProxy the {@code TransactionProxy} used to join the
	 *                 current transaction when a report is logged.
	 *
	 * @see TransactionAwareLogManager#configure(Properties,
	 *                                           TransactionProxy);
	 */
	// NOTE: This method does not have a race condition with addHandler due
	// the separate call chain having already acquired lock a on the
	// TxnAwareLogManager.  Therefore neither methods of this class require
	// locks
	void configure(TransactionProxy txnProxy) {
 	    if (txnProxy == null) {
 		return;
            }
	    
	    this.txnProxy = txnProxy;
	    
	    // In the event that no handlers have been specified for this
	    // logger, we walk the Logger hierarchy until we find a parent that
	    // does have a handler set.  The attachParentHandlers will bind
	    // those handlers to the current Logger, which results in them
	    // being wrapped by a TransactionalHandler
	    if (getHandlers().length == 0) {
		attachParentHandlers();
	    } else {
                // If handlers have been assigned to this Logger, wrap them in
                // TransactionalHandlers
                
		for (Handler h : getHandlers()) {
		    // check that we aren't already dealing with a handler that
		    // has already been made transactional.
		    if (h instanceof TransactionalHandler) {
			continue;
                    }
		    super.addHandler(new TransactionalHandler(txnProxy, h));
		    removeHandler(h);
		    // ensure that any log calls to this logger don't work
		    // their way up the logger hierarchy, which could result in
		    // non-transactional logging
		    setUseParentHandlers(false);
		}
	    }
	    
	    // lastly, note in the logging stream that this Logger is now
	    // transactional.
	    config("This logger now has transactional semantics");
	}

	/**
	 * Sets the parent of this {@code Logger} and calls {@link
	 * #attachParentHandlers()} if this logger has been configured to be
	 * transactional and does not yet have any handlers.
	 *
	 * @param {@inheritDoc}
	 */
	public void setParent(Logger parent) {
	    super.setParent(parent);
	    if (txnProxy != null && getHandlers().length == 0) {
		attachParentHandlers();
	    }
	}


	/**
	 * Returns the type and name of this {@code Logger} if this instance has
	 * transaction semantics, or otherwise returns the default {@link
	 * Logger#toString()}.
	 *
	 * @return a description of this logger
	 */
	public String toString() {
	    return (txnProxy == null)
		? "Non-transactional Logger:" + getName()
		: "TransactionalLogger:" + getName();
	}

    }

}

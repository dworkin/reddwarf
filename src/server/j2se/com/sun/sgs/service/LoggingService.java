/*
 * Copyright 2007 Sun Microsystems, Inc.
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
 */

package com.sun.sgs.service;

import java.util.logging.Logger;


/**
 * A service for specifying the transactional semantics for all the
 * {@code Logger} namepsaces of an application.
 * 
 * <p>
 * 
 * An implementation should support three properties in the
 * constructor, which can be set in the system properties file.
 *
 * <p><dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <b>
 *	{@code com.sun.sgs.impl.service.logging.app.namespace}
 *	</b><br>
 *	<i>Default:</i> {@code ""}
 *
 * <dd style="padding-top: .5em">The root of the application
 * namespace.  All loggers under this root will be have
 * transactional-semantics.  The {@code
 * com.sun.sgs.impl.service.logging.app.nontransactional.namespaces}
 * property may be used to mark some sub-namespaces under this root as
 * being non-transactional. <p></dd>
 *
 * <dt>	<i>Property:</i> <b>
 *	{@code com.sun.sgs.impl.service.logging.nontransactional.namespaces}
 *	</b><br>
 *	<i>Default:</i>  {@code ""}<br>
 *
 * <dd style="padding-top: .5em">A {@code :} delimited list of
 * namespaces or {@code Logger} names, of which the {@code Logger}
 * each should have nontransactional semantics. <p>
 *
 * <dt> <i>Property:</i> <b>
 *	{@code com.sun.sgs.impl.service.logging.transactional.namespaces}
 *	</b><br>
 *	<i>Default:</i> {@code ""}
 *
 * <dd style="padding-top: .5em">A {@code :} delimited list of
 * namespaces or {@code Logger} names, of which the {@code Logger}
 * each should have transactional semantics.  This allows developers
 * to mark sub-namespaces that had been marked as non-transaction as
 * still being trasactional.  Since all namespaces are transactional
 * by default, this option is ignored if {@code
 * com.sun.sgs.impl.service.logging.app.nontransactional.namespaces}
 * has not been set. <p></dd>
 * 
 * </dl>
 *
 * <p>
 *
 * The effects of these properties should be applied in this order:,
 * <br>If the {@code com.sun.sgs.impl.service.logging.app.namespace}
 * is provided, all {@code Logger} instances in this namespace will be
 * marked as transactional.  If this property is not specified, no
 * action is taken by this service.
 *
 * <p>
 *
 * Next, if the {@code
 * com.sun.sgs.impl.service.logging.app.nontransactional.namespaces}
 * property is specified, the {@code Logger} instances associated with
 * these namespaces are reverted to being non-transactional.
 *
 * <p>
 *
 * Lastly, if both of the previously mentioned properties have
 * specified, the {@code
 * com.sun.sgs.impl.service.logging.app.transactional.namespaces}
 * property is used to mark the {@code Logger} instances of any
 * previously-marked, non-transactional namespaces as being
 * transactional.  This property should only be used to specified
 * sub-namespaces of those specified by the {@code
 * com.sun.sgs.impl.service.logging.app.nontransactional.namespaces}
 * property.
 */
public interface LoggingService extends Service {

    /**
     * Modifies the provided {@code Logger} so that it reports with
     * transactional semantics.  If the logger was already
     * transactional, nothing is done.
     *
     * @param logger the logger that will be updated to have
     *        transactional semantics
     */
    public void makeTransactional(Logger logger);

    /**
     * Modifies the provided {@code Logger} so that it reports with
     * non-transactional semantics.  If the logger was already
     * non-transactional, nothing is done.
     *
     * @param logger the logger that will be updated to have
     *        non-transactional semantics
     */
    public void makeNonTransactional(Logger logger);

}
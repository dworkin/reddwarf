/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.service;

/**
 * Defines a <code>TransactionParticipant</code> that does not maintain durable
 * persistent state of its own, but instead relies on other participants to
 * store any persistent state that it needs.  Because these participants do not
 * need to make any persistent changes on commit or abort, the transaction
 * coordinator is not required to inform these participants of the status of a
 * prepared transaction if it can be certain that the participant has crashed.
 * Transaction coordinators can use this information to optimize their
 * implementation of the two phase commit protocol.
 */
public interface NonDurableTransactionParticipant
    extends TransactionParticipant
{
}

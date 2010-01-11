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
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
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

/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.util;

import com.sun.sgs.service.NonDurableTransactionParticipant;

/**
 * Provides a testing implementation of a non-durable transaction participant.
 */
public class DummyNonDurableTransactionParticipant
    extends DummyTransactionParticipant
    implements NonDurableTransactionParticipant
{
    /** Creates an instance of this class. */
    public DummyNonDurableTransactionParticipant() { }
}

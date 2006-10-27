package com.sun.sgs.test;

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

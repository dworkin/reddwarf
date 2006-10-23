package com.sun.sgs.test;

import com.sun.sgs.service.NonDurableTransactionParticipant;

public class DummyNonDurableTransactionParticipant
    extends DummyTransactionParticipant
    implements NonDurableTransactionParticipant
{
    public DummyNonDurableTransactionParticipant() { }
}

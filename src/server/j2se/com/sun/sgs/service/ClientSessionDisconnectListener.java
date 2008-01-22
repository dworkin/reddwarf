package com.sun.sgs.service;

import java.math.BigInteger;

public interface ClientSessionDisconnectListener {
    void disconnected(BigInteger sessionRefId);
}

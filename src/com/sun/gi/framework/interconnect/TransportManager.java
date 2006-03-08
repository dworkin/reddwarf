package com.sun.gi.framework.interconnect;

import java.io.IOException;

public interface TransportManager {
  TransportChannel openChannel(String channelName) throws IOException;


}

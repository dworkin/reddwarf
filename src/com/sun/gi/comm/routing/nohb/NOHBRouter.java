package com.sun.gi.comm.routing.nohb;

import java.io.*;
import java.nio.*;
import java.security.*;
import java.util.*;

import com.sun.gi.comm.routing.*;
import com.sun.gi.framework.interconnect.*;
import java.util.Map.Entry;

/**
 * This is an implementation of the Router interface that uses JRMS reliable
 * multicast, is totally stateless, and requires no heartbeats.
 * <p>Title: NOHBRouter</p>
 * <p>Description: A heartbeatless implementation of the Router interface</p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: Sun Microsystems, TMI</p>
 * @author Jeff Kesselman
 * @version 1.0
 */

public class NOHBRouter
    implements Router, TransportChannelListener {
  int gameID;
  TransportChannel chan;
  List listeners = new ArrayList();
  IDKeyRegistry keyRegistry;
  transient static SecureRandom random = null;
  private List myUsers = new ArrayList();
  private static final byte OP_BROADCAST = 0;
  private static final byte OP_IDDESTROYED = 1;
  private static final byte OP_IDKEY = 2;
  private static final byte OP_MULTICAST = 3;
  private static final byte OP_UNICAST = 4;
  private static final long KEY_TIMEOUT = 5000;
  private long keyTimeout;

  /**
   * NOHBRouter
   *
   * @param transportManager TransportManager
   * @param gameID int
   */
  public NOHBRouter(TransportManager transportManager, int gameID) throws
      InstantiationException {
    this.gameID = gameID;
    try {
      chan = transportManager.openChannel("_NOHBrouter_game" + gameID);
      chan.addListener(this);
    }
    catch (IOException ex) {
      ex.printStackTrace();
      throw new InstantiationException();
    }
    if (random == null) {
      try {
        random = SecureRandom.getInstance("SHA1PRNG");
      }
      catch (NoSuchAlgorithmException ex) {
        ex.printStackTrace();
      }
    }
    keyTimeout = KEY_TIMEOUT;
    String timeoutProperty = System.getProperty("sgs.router.keytimeout");
    if (timeoutProperty != null) {
      keyTimeout = Long.parseLong(timeoutProperty);
    }
    keyRegistry = new IDKeyRegistry(this, keyTimeout);
    new Thread(new Runnable() {
      /**
       * run
       */
      public void run() {
        while (true) {
          synchronized (myUsers) {
            keyRegistry.renewKeys(myUsers);
          }
          keyRegistry.expireKeys();
          try {
            Thread.sleep(NOHBRouter.this.keyTimeout / 2);
          }
          catch (Exception e) {
            e.printStackTrace();
          }
        }
      }

    }).start(); ;

  }

  /**
   * addRouterListener
   *
   * @param listener RouterListener
   */
  public void addRouterListener(RouterListener listener) {
    listeners.add(listener);
  }

  /**
   * broadcastData
   *
   * @param from UserID
   * @param message ByteBuffer
   * @param reliable boolean
   */
  public void broadcastData(UserID from, ByteBuffer message, boolean reliable) {
    ByteBuffer hdr = ByteBuffer.allocate(2 + from.ioByteSize());
    hdr.put(OP_BROADCAST);
    hdr.put( (byte) (reliable ? 1 : 0));
    from.write(hdr);
    chan.sendData(new ByteBuffer[] {hdr, message.duplicate()});
  }

  /**
   * fireBroadcastDataArrived
   *
   * @param from UserID
   * @param buff ByteBuffer
   * @param reliable boolean
   */
  private void fireBroadcastDataArrived(UserID from, ByteBuffer buff,
                                        boolean reliable) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (RouterListener) i.next()).broadcastDataArrived(from, buff.duplicate(),
          reliable);
    }
  }

  /**
   * createUser
   *
   * @param bytes byte[]
   * @return UserID
   */
  public UserID createUser(byte[] bytes) throws InstantiationException {
    return new NOHBUserID(bytes);
  }

  /**
   * createUser
   *
   * @return UserID
   */
  public UserID createUser() {
    UserID id = new NOHBUserID();
    synchronized (myUsers) {
      myUsers.add(id);
    }
    return id;
  }

  public byte[] initializeIDKey(UserID id) {
    long key = random.nextLong();
    sendIDkey(id, key);
    byte[] keybytes = new byte[8];
    keybytes[0] = (byte) (key >> 56);
    keybytes[1] = (byte) (key >> 48);
    keybytes[2] = (byte) (key >> 40);
    keybytes[3] = (byte) (key >> 32);
    keybytes[4] = (byte) (key >> 24);
    keybytes[5] = (byte) (key >> 16);
    keybytes[6] = (byte) (key >> 8);
    keybytes[7] = (byte) (key >> 0);
    return keybytes;
  }

  /**
   * sendIDkey
   *
   * @param id UserID
   * @param key long
   */

  private void sendIDkey(UserID id, long key) {
    ByteBuffer hdr = ByteBuffer.allocate(1 + id.ioByteSize() + 16);
    hdr.put(OP_IDKEY);
    id.write(hdr);
    hdr.putLong(key);
    try {
      chan.sendData(hdr);
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * disposeUser
   *
   * @param id UserID
   */
  public void disposeUser(UserID id) {
    synchronized (myUsers) {
      myUsers.remove(id);
    }
  }

  /**
   * fireUserDropped
   *
   * @param id UserID
   */
  private void fireUserDropped(UserID id) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (RouterListener) i.next()).userDropped(id);
    }
  }

  /**
   * sendUserLeft
   *
   * @param id UserID
   */
  private void sendUserLeft(UserID id) {
    ByteBuffer hdr = ByteBuffer.allocate(1 + id.ioByteSize());
    hdr.put(OP_IDDESTROYED);
    id.write(hdr);
    try {
      chan.sendData(hdr);
    }
    catch (IOException ex) {
      ex.printStackTrace();
    }
  }

  /**
   * unicastData
   *
   * @param target UserID
   * @param from UserID
   * @param message ByteBuffer
   * @param reliable boolean
   */
  public void unicastData(UserID target, UserID from, ByteBuffer message,
                          boolean reliable) {
    ByteBuffer hdr = ByteBuffer.allocate(3 + from.ioByteSize()+target.ioByteSize());
    hdr.put(OP_UNICAST);
    hdr.put( (byte) (reliable ? 1 : 0));
    from.write(hdr);
    target.write(hdr);
    chan.sendData(new ByteBuffer[] {hdr, message.duplicate()});

  }

  /**
   * fireUnicastDataArrived
   *
   * @param target UserID
   * @param from UserID
   * @param message ByteBuffer
   * @param reliable boolean
   */
  private void fireUnicastDataArrived(UserID target, UserID from,
                                      ByteBuffer message, boolean reliable) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (RouterListener) i.next()).dataArrived(target, from, message.duplicate(), reliable);
    }
  }

  /**
   * multicastData
   *
   * @param targets UserID[] A list of targets tos end message to
   * (targets.length() currently must be < 256)
   * @param from UserID
   * @param message ByteBuffer
   * @param reliable boolean
   */
  public void multicastData(UserID[] targets, UserID from, ByteBuffer message,
                            boolean reliable) {
    if (targets.length > 255) {
      throw new UnsupportedOperationException(
          "targets.length must be less then 255.");
    }
    ByteBuffer hdr = ByteBuffer.allocate(3 + from.ioByteSize() +
                                         (targets[0].ioByteSize() *
                                          targets.length));
    hdr.put(OP_MULTICAST);
    hdr.put( (byte) (reliable ? 1 : 0));
    from.write(hdr);
    hdr.put( (byte) targets.length);
    for (int i = 0; i < targets.length; i++) {
      targets[i].write(hdr);
    }
    chan.sendData(new ByteBuffer[] {hdr, message.duplicate()});
  }

  /**
   * fireMulticastDataArrived
   *
   * @param targets UserID[]
   * @param from UserID
   * @param message ByteBuffer
   * @param reliable boolean
   */
  private void fireMulticastDataArrived(UserID[] targets, UserID from,
                                        ByteBuffer message, boolean reliable) {
    for (int i = 0; i < targets.length; i++) {
      fireUnicastDataArrived(targets[i], from, message.duplicate(), reliable);
    }
  }

  /**
   * reregisterUser
   *
   * @param id UserID
   * @param key byte[]
   * @return boolean
   */
  public boolean reregisterUser(UserID id, byte[] key) {
    long lkey = ( ( ( (long) key[0]) & 0xFF) << 56) |
        ( ( ( (long) key[1]) & 0xFF) << 48) |
        ( ( ( (long) key[2]) & 0xFF) << 40) |
        ( ( ( (long) key[3]) & 0xFF) << 32) |
        ( ( ( (long) key[4]) & 0xFF) << 24) | ( ( ( (long) key[5]) & 0xFF) << 16) |
        ( ( ( (long) key[6]) & 0xFF) << 8) | ( ( (long) key[7]) & 0xFF);
    System.out.println("Got key = "+lkey);

    return keyRegistry.attemptReregister(id, lkey);
  }

  /**
   * channelClosed
   */
  public void channelClosed() {
    System.out.println("PANIC ERROR: Game router channel suddenly closed!");
    System.exit(2001);
  }

  /**
   * fireAddUser
   *
   * @param id UserID
   */
  private void fireAddUser(UserID id) {
    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (RouterListener) i.next()).userAdded(id);
    }
  }

  /**
   * dataArrived
   *
   * @param buff ByteBuffer
   */
  public void dataArrived(ByteBuffer buff) {
    byte op = buff.get();
    switch (op) {
      case OP_BROADCAST:
        boolean reliable = (buff.get() == (byte) 1);
        UserID from = new NOHBUserID(buff);
        fireBroadcastDataArrived(from, buff.slice(), reliable);
        break;
      case OP_IDDESTROYED:
        UserID id = new NOHBUserID(buff);
        keyRegistry.deregisterKey(id);
        break;
      case OP_IDKEY:
        id = new NOHBUserID(buff);
        long newkey = buff.getLong();
        keyRegistry.registerKey(id, newkey);
        break;
      case OP_MULTICAST:
         reliable = (buff.get() == (byte) 1);
        from = new NOHBUserID(buff);
        byte targetCount = buff.get();
        UserID targets[] = new UserID[targetCount];
        for (int i = 0; i < targetCount; i++) {
          targets[i] = new NOHBUserID(buff);
        }
        fireMulticastDataArrived(targets, from, buff.slice(), reliable);
        break;
      case OP_UNICAST:
        reliable = (buff.get() == (byte) 1);
        from = new NOHBUserID(buff);
        UserID target = new NOHBUserID(buff);
        fireUnicastDataArrived(target, from, buff.slice(), reliable);
        break;
      default:
        System.out.println("UNKNOW OP RECEIVED: " + op);
        break;
    }
  }

  /**
   * fireNewIDKey
   *
   * @param id UserID
   * @param newkey long
   */
  private void fireNewIDKey(UserID id, long key) {
    System.out.println("Sending key = "+key);
    byte[] keybytes = new byte[8];
    keybytes[0] = (byte) (key >> 56);
    keybytes[1] = (byte) (key >> 48);
    keybytes[2] = (byte) (key >> 40);
    keybytes[3] = (byte) (key >> 32);
    keybytes[4] = (byte) (key >> 24);
    keybytes[5] = (byte) (key >> 16);
    keybytes[6] = (byte) (key >> 8);
    keybytes[7] = (byte) (key >> 0);

    for (Iterator i = listeners.iterator(); i.hasNext(); ) {
      ( (RouterListener) i.next()).newUserKey(id, keybytes);
    }
  }

  class IDKeyRegistry {
    NOHBRouter router;
    Map currentIDKeys = new HashMap();
    Map oldIDKeys = new HashMap();
    Map idToTimeouts = new HashMap();
    long timeoutLength;

    public IDKeyRegistry(NOHBRouter router, long timeout) {
      this.router = router;
      timeoutLength = timeout;
    }

    public synchronized void registerKey(UserID id, long key) {
      Long oldKey = (Long) currentIDKeys.get(id);
      if (oldKey != null) {
        oldIDKeys.put(id, oldKey);
      }
      currentIDKeys.put(id, new Long(key));
     idToTimeouts.put(id, new Long(System.currentTimeMillis() +timeoutLength));
      if (oldKey == null) { // new user
        router.fireAddUser(id);
      }

    }

    public synchronized void deregisterKey(UserID id) {
      currentIDKeys.remove(id);
      oldIDKeys.remove(id);
      router.fireUserDropped(id);
    }

    public synchronized boolean attemptReregister(UserID id, long key) {
      Long currentKey = (Long) currentIDKeys.get(id);
      if ( (currentKey != null) && (currentKey.longValue() == key)) {
        idToTimeouts.put(id,new Long(System.currentTimeMillis() +
                                          timeoutLength));
        myUsers.add(id);
        router.sendIDkey(id, currentKey.longValue()); // tel leveryoen thsi key is renewed
        return true;
      }
      Long oldKey = (Long) oldIDKeys.get(id);
      if ( (oldKey != null) && (oldKey.longValue() == key)) {
        idToTimeouts.put(id,new Long(System.currentTimeMillis() +
                                          timeoutLength));
        myUsers.add(id);
        router.sendIDkey(id, oldKey.longValue()); // tell everyone we resurrectee old key
        return true;
      }
      return false;
    }

    public synchronized void renewKeys(List myUsers) {
      // issue new keys
      for (Iterator i = myUsers.iterator(); i.hasNext(); ) {
        UserID id = (UserID) i.next();
        long newkey = NOHBRouter.random.nextLong();
        router.sendIDkey(id, newkey);
        router.fireNewIDKey(id, newkey);
      }

    }

    public synchronized void expireKeys() {
      List renewalList = new ArrayList();
      // System.out.println("Current Time: "+System.currentTimeMillis());
      for (Iterator i = idToTimeouts.entrySet().iterator(); i.hasNext(); ) {
        Entry entry = (Entry)i.next();
        Long timeout = (Long)entry.getValue();
        // System.out.println(" ++ Timeout: "+timeout);
        if (timeout.longValue() < System.currentTimeMillis()) {
          UserID id = (UserID) entry.getKey();
          Long currentKey = (Long) currentIDKeys.get(id);
          if (currentKey != null) {
            oldIDKeys.put(id, currentKey);
            currentIDKeys.remove(id);
            renewalList.add(id);
          }
          else {
            if (oldIDKeys.containsKey(id)) {
              oldIDKeys.remove(id);
              i.remove();
              router.fireUserDropped(id);
            }
          }
        }
      }
      for (Iterator i = renewalList.iterator(); i.hasNext(); ) {
        idToTimeouts.put((UserID) i.next(),new Long(
                                          System.currentTimeMillis() +
                                          timeoutLength));
      }
    }

  }

  class TimeoutRecord {
    long key;
    long timeout;
    public TimeoutRecord(long ky, long timeout) {
      this.key = key;
      this.timeout = timeout;
    }
  }
}

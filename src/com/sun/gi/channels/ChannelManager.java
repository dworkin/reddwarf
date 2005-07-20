package com.sun.gi.channels;

/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2003</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */

import java.io.IOException;
import com.sun.gi.utils.SGSUUID;

public interface ChannelManager {

    /**
     * This method is invoked in order to log a player into the channel system.
     * @param playerName A player name for display purposes. (May or may not be
     * required to be unique according to the underlying server's login
     * requirements.)
     * @param password A login password.  Depending on the system this may be
     * per player (in which case name must be unique) or it may be a system-wide
     * password.
     * @param playerInfo A block of data to be associated with the player.
     * (The format and details are system dependant.)
     * @return This returns a player Key used to identify the player for
     * permissions purposes for the duration of this session.*/
    public Key loginPlayer(SGSUUID appID, String playerName, String password,
                           byte[] playerInfo,ChannelManagerListener listener);
    /**
     * This method is invoked in order to create a packet channel. The channel
     * may or may not make any gaurantees about delivery depending on how
     * the channel type flags (reliable, inOrder) are set.
     *
     * @param channelName A non-unique name for the channel.
     * @param channelInfo A packet of data to be associated with the channel.
     * (The format and content is game specific.)
     * @param listener A  listener object to recieve channel events.
     * @param multicast Set true if this is a multi-cast channel.
     * @return A channel key used to identify the channel owner for
     * administrative purposes.
     */
    public Key createChannel(String channelName, byte[] channelInfo,
                       boolean reliable, boolean inOrder,boolean owned);

    /**
      * This method adds a player to a channel. To be successful the caller
      * must pass in the channel key returned from createChannel(...).
      * @param playerID The ID of the player to add.
      * @param channelKey The key to the channel the player is to be added to.
      */

    public void addPlayerToChannel(long channelID, long playerID, Key key,
                                   ChannelListener listener);

    //public void reuseAccessToChannel(long channelID, long playerID, Key key);
    /**
     * This method is used to remove a player from a channel.  To be successful
     * the caller must pass in the channel key returned from createChannel(...).
     * @param playerID The ID of the player to remove from the channel.
     * @param channelKey The key to the channel the player is to be removed
     * from.
     */
    public void dropPlayerFromChannel(long playerID, long channelID,
                                      Key channelKey);
    /**
     * This method is used to eliminate a channel.  All players still joined to
     * the channel will be removed and then the channel will be eliminated.
     * @param channelKey The key to the channel to be eliminated.
     */
    public void destroyChannel(Key channelKey);
    /**
     * This method logs a player out of the system. Events will be generated
     * showing the player elaving any channels theya re still joiend to and
     * then the player will be disonnected from the system.
     * @param playerKey  The key to the player to be logged out.
     */
    public void logoutPlayer(Key playerKey);
    /**
     * This method places a packet into the channel. Delivery gaurantees
     * are as set for the denoted channel at channel creation time.
     *
     * @param channelID The channel to place the packet into.
     * @param fromPlayerID The ID of the packet sender.
     * @param mask A mask that can be used be recievers to filter pacekts.
     * @param data A byte array within which the data to be sent resides.
     * @param offset The ordinal number of the first byte of the data to be
     * sent within the data array. (Often 0).
     * @param length The length of the ata to be sent. (Often the same as the
     * length of the data array.)
     */
    public void sendPacket(long channelID,  long mask, Packet packet)
        throws IOException;

    /** This method will attempt to read a data packet into the passed in packet
    * object.  If exactMatch is true then it will return the next packet whose
    * mask is equal to the passed in mask (mask1 == mask2).  If exactMatch is
    * false then it will return the next packet which has a 1 bit in common
    * with the mask (mask1 & mask2 != 0).
    *
    * Reading with this call marks the packet as read by the passed in packet.
    * It will be skipped in subsequent reads with the same packet object but
    * but will still be available to be read by other packet objects.
    *
    * If there is no match, this call returns null.
    *
    * @param channelID The ID of the channel to read from.
    * @param mask A mask to strain the packets
    * @param exactMatch If true, mask matching is doen by equality.  If false
    * then the mask match test is a bitwise AND.
    * @return TRUE if data was read.
    * @throws IOException If a packet is used whose type flags do not match the
    * flags set on creation of the channel, a PacketMismatchException is thrown.
    * If an IO error occurrs the appropriate IOException will be thrown.
    */

    public boolean readPacket(long channelID, long playerID, long mask,
                              boolean exactMatch, Packet packet)
        throws IOException;
    /** This method will attempt to read a data packet into the passed in packet
    * object.  If exactMatch is true then it will return the next packet whose
    * mask is equal to the passed in mask (mask1 == mask2).  If exactMatch is
    * false then it will return the next packet which has a 1 bit in common
    * with the mask (mask1 & mask2 != 0).
    *
    * Reading with this call removes the packet from the channel such that it
    * cannot be read by other players.  Other players will skip this packet
    * and proceeed to the next one that can be returned to satisy a read or
    * take.
    *
    * If there is no match, this call returns null.
    *
    * @param channelID The ID of the channel to read from.
    * @param mask A mask to strain the packets
    * @param exactMatch If true, mask matching is doen by equality.  If false
    * then the mask match test is a bitwise AND.
    * @return TRUE if data was read.
    * @throws IOException If a packet is used whose type flags do not match the
    * flags set on creation of the channel, a PacketMismatchException is thrown.
    * If an IO error occurrs the appropriate IOException will be thrown.
    */
    public boolean takePacket(long channelID, long playerID, long mask,
                              boolean exactMatch,Packet packet)
        throws IOException;

    public boolean waitForPackets(long playerID, long[] channelIDs, long[] masks,
                                  boolean[] exactMatches);


    /**
     * This method allocates a Packet object with all of the packet type
     * fields set appropriately for the spcified channel.
     * It will allocate a fixed-length packet with a maximum data-space the size
     * indictaed in the length parameter.
     *
     * @param channelID  The channel this packet will be sent or read on.
     * @param length A fixed maximum data size for the packet.
     * @return The newly allocated Packet object.
     *
     */
    public Packet createPacket(long channelID, int length);
    /**
     * This method allocates a Packet object with all of the packet type
     * fields set appropriately for the spcified channel.
     * The packet's data-space will begin with the size specified in the
     * length parameter.  If the groewable parameter is true then that size
     * will grow as needed to accomodate data read/written with it.  (Likely
     * producing garbage in the process.)
     *
     * createPacket(...,...,false) is functionally equivalent to
     * createPacket(...,...)
     *
     * @param channelID  The channel this packet will be sent or read on.
     * @param length A fixed maximum data size for the packet.
     * @param growable If true then the data space will be grown as necessary to
     * fulfill read/write requests.
     * @return The newly allocated Packet object.
     */

    public Packet createPacket(long channelID, int length, boolean growable);

    public long[] listChannels();
    public String getChannelName(long id);
    public byte[] getChannelInfo(long id);
    public String getPlayerName(long playerID);
    public byte[] getPlayerInfo(long playerID);
}
/**
 * 
 */
package com.sun.sgs.example.chat.app;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Channel;
import com.sun.sgs.app.ClientSession;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.Delivery;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;

/**
 * @author jm199043
 *
 */
public class ChatChannel
    implements Serializable, ManagedObject, ManagedObjectRemoval
{
    private static final long serialVersionUID = 1L;

    private final String name;
    private final Set<ManagedReference> membersImpl;
    private final ManagedReference channelRef;

    /**
     * TODO
     * 
     * @param channelName
     * @return
     * @throws NameNotBoundException
     */
    public static ChatChannel find(String channelName) {
        DataManager dataMgr = AppContext.getDataManager();
        String key = channelKey(channelName);
        return dataMgr.getBinding(key, ChatChannel.class);
    }

    /**
     * TODO
     * 
     * @param channelName
     * @return
     */
    public static ChatChannel findOrCreate(String channelName) {
        DataManager dataMgr = AppContext.getDataManager();
        String key = channelKey(channelName);
        try {
            return dataMgr.getBinding(key, ChatChannel.class);
        } catch (NameNotBoundException e) {
            ChatChannel channel = new ChatChannel(channelName);
            dataMgr.setBinding(key, channel);
            return channel;
        }
    }

    public boolean hasMembers() {
        return members().isEmpty();
    }

    public Set<ManagedReference> memberRefs() {
        return Collections.unmodifiableSet(members());
    }

    public void send(ByteBuffer message) {
        channel().send(message);
    }

    public boolean join(ClientSession session) {
        DataManager dataMgr = AppContext.getDataManager();
        dataMgr.markForUpdate(this);
        ManagedReference sessionRef = dataMgr.createReference(session);
        channel().join(session);
        return members().add(sessionRef);
    }

    public boolean leave(ClientSession session) {
        DataManager dataMgr = AppContext.getDataManager();
        dataMgr.markForUpdate(this);
        ManagedReference sessionRef = dataMgr.createReference(session);
        channel().leave(session);
        return members().remove(sessionRef);
    }

    public void close() {
        AppContext.getDataManager().removeObject(this);
    }

    /**
     * TODO
     */
    @Override
    public void removingObject() {
        try {
            AppContext.getDataManager().removeBinding(channelKey(name));
        } catch (NameNotBoundException e) {
            // ignore
        }
        channel().close();
    }

    private Channel channel() {
        // No null-check needed
        return channelRef.get(Channel.class);
    }
    
    private Set<ManagedReference> members() {
        return membersImpl;
    }

    /**
     * TODO
     */
    protected ChatChannel(String name) {
        this.name = name;
        membersImpl = new HashSet<ManagedReference>();
        Channel channel = 
            AppContext.getChannelManager().createChannel(Delivery.RELIABLE);
        channelRef = AppContext.getDataManager().createReference(channel);
    }

    /**
     * TODO
     * 
     * @param channelName
     * @return
     */
    protected static String channelKey(String channelName) {
        return "ChatApp.ChatChannel." + channelName;
    }

}

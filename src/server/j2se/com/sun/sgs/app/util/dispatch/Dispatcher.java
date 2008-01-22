package com.sun.sgs.app.util.dispatch;

public interface Dispatcher<T extends DispatchKey> {
    public void register(T key, DispatchListener listener);
    public void unregister(T key);
}

package com.demo.upimesh.service;

import java.util.ArrayList;
import java.util.List;

/**
 * A list that gossip threads put packets into, and the main thread takes from.
 * wait / notify is the whole communication mechanism. One lock: this inbox.
 */
public class PacketInbox<T> {

    private final List<T> waiting = new ArrayList<>();

    public synchronized void add(T item) {
        waiting.add(item);
        notifyAll();
    }

    public synchronized T take() throws InterruptedException {
        while (waiting.isEmpty()) {
            wait();
        }
        return waiting.remove(0);
    }
}

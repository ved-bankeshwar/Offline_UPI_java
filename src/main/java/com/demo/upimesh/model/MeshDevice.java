package com.demo.upimesh.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A phone in the mesh. Packets sit in a list. A set of packet ids stops duplicates.
 * Subclasses say whether the phone has internet, via @MeshNode.
 */
public abstract class MeshDevice implements PacketCarrier {

    private final String deviceId;
    private final List<MeshPacket> held = new ArrayList<>();
    private final Set<String> seen = new HashSet<>();

    protected MeshDevice(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    /**
     * Reads @MeshNode on the concrete class (OfflineDevice or BridgeDevice).
     */
    public boolean hasInternet() {
        MeshNode mark = getClass().getAnnotation(MeshNode.class);
        return mark != null && mark.internet();
    }

    public synchronized boolean hold(MeshPacket packet) {
        if (packet == null || packet.getPacketId() == null) {
            return false;
        }
        if (!seen.add(packet.getPacketId())) {
            return false;
        }
        held.add(packet);
        return true;
    }

    public synchronized boolean holds(String packetId) {
        return seen.contains(packetId);
    }

    public synchronized List<MeshPacket> getHeldPackets() {
        return new ArrayList<>(held);
    }

    public synchronized int packetCount() {
        return held.size();
    }

    public synchronized void clear() {
        held.clear();
        seen.clear();
    }
}

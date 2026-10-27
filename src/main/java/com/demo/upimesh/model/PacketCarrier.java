package com.demo.upimesh.model;

import java.util.List;

/**
 * Anything that can hold mesh packets: a phone, or later a different kind of node.
 */
public interface PacketCarrier {

    String getDeviceId();

    boolean hold(MeshPacket packet);

    boolean holds(String packetId);

    List<MeshPacket> getHeldPackets();

    int packetCount();
}

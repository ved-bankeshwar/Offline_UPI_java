package com.demo.upimesh;

import com.demo.upimesh.io.MeshException;
import com.demo.upimesh.io.MeshPacketFileStore;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.MeshTopology;
import com.demo.upimesh.model.Payer;
import com.demo.upimesh.model.Vpa;
import com.demo.upimesh.service.MeshSimulatorService;
import com.demo.upimesh.service.PacketInbox;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Small checks for the plain-Java pieces: addresses, the neighbour table, and one gossip round. */
class ReadableMeshTest {

    @Test
    void vpaKeepsNameAtDemo() {
        assertEquals("alice@demo", new Vpa("  Alice@DEMO ").text());
        assertThrows(IllegalArgumentException.class, () -> new Vpa("alice"));
    }

    @Test
    void knownPayerUsesThePhoneFromTheTable() {
        assertEquals("phone-stranger1", Payer.ofVpa("bob@demo").getDeviceId());
        assertEquals("Bob", Payer.ofVpa("bob@demo").getHolderName());
    }

    @Test
    void aPhoneDoesNotHearItself() {
        MeshTopology topology = new MeshTopology(new String[] { "a", "b" });
        assertFalse(topology.inRange(0, 0));
        assertTrue(topology.inRange(0, 1));
    }

    @Test
    void inboxWaitsUntilAWorkerAddsSomething() throws Exception {
        PacketInbox<String> inbox = new PacketInbox<>();
        Thread worker = new Thread(() -> inbox.add("hello"), "phone-alice");
        worker.start();
        assertEquals("hello", inbox.take());
        worker.join();
    }

    @Test
    void oneGossipRoundCopiesThePacketToTheBridge() {
        MeshPacketFileStore files = new MeshPacketFileStore("target/test-mesh/readable-gossip.ser");
        files.clear();
        MeshSimulatorService mesh = new MeshSimulatorService(files);

        MeshPacket packet = new MeshPacket();
        packet.setPacketId("packet-1");
        packet.setTtl(5);
        packet.setCreatedAt(1L);
        packet.setCiphertext("abc");
        mesh.inject("phone-alice", packet);

        MeshSimulatorService.GossipResult result = mesh.gossipOnce();
        assertEquals(4, result.transfers());
        assertTrue(mesh.getDevice("phone-bridge").holds("packet-1"));
        assertEquals(4, mesh.getDevice("phone-bridge").getHeldPackets().get(0).getTtl());
        assertEquals(5, mesh.getDevice("phone-alice").getHeldPackets().get(0).getTtl());
        assertTrue(mesh.getDevice("phone-bridge").hasInternet());
        assertFalse(mesh.getDevice("phone-alice").hasInternet());
    }

    @Test
    void aSavedPacketIsThereAfterRestart() {
        MeshPacketFileStore files = new MeshPacketFileStore("target/test-mesh/roundtrip.ser");
        files.clear();
        MeshSimulatorService first = new MeshSimulatorService(files);
        MeshPacket packet = new MeshPacket();
        packet.setPacketId("packet-9");
        packet.setTtl(5);
        packet.setCreatedAt(1L);
        packet.setCiphertext("abc");
        first.inject("phone-alice", packet);

        MeshSimulatorService second = new MeshSimulatorService(files);
        second.restorePackets();
        assertTrue(second.getDevice("phone-alice").holds("packet-9"));
    }

    @Test
    void savingOntoADirectoryThrowsMeshException() {
        MeshPacketFileStore files = new MeshPacketFileStore("target/test-mesh");
        assertThrows(MeshException.class, () -> files.save(List.of()));
    }
}

package com.demo.upimesh.service;

import com.demo.upimesh.io.MeshException;
import com.demo.upimesh.io.MeshPacketFileStore;
import com.demo.upimesh.model.BridgeDevice;
import com.demo.upimesh.model.MeshDevice;
import com.demo.upimesh.model.MeshPacket;
import com.demo.upimesh.model.MeshTopology;
import com.demo.upimesh.model.OfflineDevice;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Five phones in one room. Gossip starts one thread per phone. Each thread only
 * drops copies into a shared inbox. This thread then stores them on the phones.
 * Phones never lock each other, so the round cannot deadlock.
 */
@DependsOn("serverKeyHolder")
@Service
public class MeshSimulatorService {

    private static final Logger log = LoggerFactory.getLogger(MeshSimulatorService.class);

    private final MeshDevice[] phones;
    private final MeshTopology topology;
    private final MeshPacketFileStore packetFiles;

    public MeshSimulatorService(MeshPacketFileStore packetFiles) {
        this.packetFiles = packetFiles;
        String[] ids = {
                "phone-alice",
                "phone-stranger1",
                "phone-stranger2",
                "phone-stranger3",
                "phone-bridge"
        };
        this.topology = new MeshTopology(ids);
        this.phones = new MeshDevice[ids.length];
        for (int i = 0; i < ids.length; i++) {
            if ("phone-bridge".equals(ids[i])) {
                phones[i] = new BridgeDevice(ids[i]);
            } else {
                phones[i] = new OfflineDevice(ids[i]);
            }
        }
    }

    @PostConstruct
    public void restorePackets() {
        packetFiles.restore(Arrays.asList(phones));
    }

    public Collection<MeshDevice> getDevices() {
        return Arrays.asList(phones);
    }

    public MeshDevice getDevice(String id) {
        for (MeshDevice phone : phones) {
            if (phone.getDeviceId().equals(id)) {
                return phone;
            }
        }
        return null;
    }

    /** Sender drops a packet onto their own phone. */
    public synchronized void inject(String senderDeviceId, MeshPacket packet) {
        MeshDevice sender = getDevice(senderDeviceId);
        if (sender == null) {
            throw new IllegalArgumentException("Unknown device: " + senderDeviceId);
        }
        sender.hold(packet);
        remember();
        log.info("Packet {} injected at {} (TTL={})",
                packet.getPacketId().substring(0, 8), senderDeviceId, packet.getTtl());
    }

    /**
     * One round. Each phone looks at what it held at the start and offers a
     * copy to every neighbour in the topology table. TTL drops by one.
     */
    public synchronized GossipResult gossipOnce() {
        Map<String, List<MeshPacket>> snapshot = new LinkedHashMap<>();
        for (MeshDevice phone : phones) {
            snapshot.put(phone.getDeviceId(), phone.getHeldPackets());
        }

        PacketInbox<Delivery> inbox = new PacketInbox<>();
        Thread[] workers = new Thread[phones.length];
        int expected = 0;

        for (int from = 0; from < phones.length; from++) {
            List<Delivery> outgoing = new ArrayList<>();
            for (MeshPacket packet : snapshot.get(phones[from].getDeviceId())) {
                if (!packet.nextHop().canForward()) {
                    continue;
                }
                int to = 0;
                for (boolean canHear : topology.neighbours(from)) {
                    if (canHear && !phones[to].holds(packet.getPacketId())) {
                        outgoing.add(new Delivery(phones[to], packet.nextHop().forward()));
                    }
                    to = to + 1;
                }
            }
            expected = expected + outgoing.size();
            workers[from] = new Thread(new PhoneForwarder(outgoing, inbox), phones[from].getDeviceId());
            workers[from].start();
        }

        int transfers = 0;
        try {
            for (int n = 0; n < expected; n++) {
                Delivery delivery = inbox.take();
                if (delivery.to.hold(delivery.packet)) {
                    transfers++;
                }
            }
            for (Thread worker : workers) {
                worker.join();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gossip was interrupted", ex);
        }

        remember();
        log.info("Gossip round complete: {} packet transfers", transfers);
        return new GossipResult(transfers, snapshotMap());
    }

    private void remember() {
        try {
            packetFiles.save(Arrays.asList(phones));
        } catch (MeshException ex) {
            throw new IllegalStateException(ex.getMessage(), ex);
        }
    }

    public Map<String, Integer> snapshotMap() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (MeshDevice phone : phones) {
            counts.put(phone.getDeviceId(), phone.packetCount());
        }
        return counts;
    }

    /** Packets sitting on phones that have internet. Flush uploads these. */
    public synchronized List<BridgeUpload> collectBridgeUploads() {
        List<BridgeUpload> out = new ArrayList<>();
        for (MeshDevice phone : bridges()) {
            for (MeshPacket packet : phone.getHeldPackets()) {
                out.add(new BridgeUpload(phone.getDeviceId(), packet));
            }
        }
        return out;
    }

    public synchronized void resetMesh() {
        for (MeshDevice phone : phones) {
            phone.clear();
        }
        packetFiles.clear();
    }

    private List<MeshDevice> bridges() {
        return Arrays.stream(phones).filter(phone -> phone.hasInternet()).toList();
    }

    /** A worker's only job: put its copies in the inbox. It does not touch the phones. */
    private static class PhoneForwarder implements Runnable {
        private final List<Delivery> deliveries;
        private final PacketInbox<Delivery> inbox;

        PhoneForwarder(List<Delivery> deliveries, PacketInbox<Delivery> inbox) {
            this.deliveries = deliveries;
            this.inbox = inbox;
        }

        public void run() {
            for (Delivery delivery : deliveries) {
                inbox.add(delivery);
            }
        }
    }

    private static class Delivery {
        final MeshDevice to;
        final MeshPacket packet;

        Delivery(MeshDevice to, MeshPacket packet) {
            this.to = to;
            this.packet = packet;
        }
    }

    public record GossipResult(int transfers, Map<String, Integer> deviceCounts) {}
    public record BridgeUpload(String bridgeNodeId, MeshPacket packet) {}
}

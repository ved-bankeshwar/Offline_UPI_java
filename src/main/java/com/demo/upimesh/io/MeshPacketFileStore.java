package com.demo.upimesh.io;

import com.demo.upimesh.model.MeshDevice;
import com.demo.upimesh.model.MeshPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Writes each simulated phone's packets to disk with an object stream.
 * Restarting the app puts those packets back in the mesh.
 */
@Component
public class MeshPacketFileStore {

    private static final Logger log = LoggerFactory.getLogger(MeshPacketFileStore.class);

    private final Path path;

    public MeshPacketFileStore(@Value("${upi.mesh.packet-file:data/mesh/packets.ser}") String file) {
        this.path = Path.of(file);
    }

    public synchronized void save(Collection<MeshDevice> devices) throws MeshException {
        HashMap<String, ArrayList<MeshPacket>> snapshot = new HashMap<>();
        for (MeshDevice device : devices) {
            snapshot.put(device.getDeviceId(), new ArrayList<>(device.getHeldPackets()));
        }
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (ObjectOutputStream out = new ObjectOutputStream(
                    new BufferedOutputStream(Files.newOutputStream(path)))) {
                out.writeObject(snapshot);
            }
        } catch (IOException e) {
            throw new MeshException("Could not save mesh packets to " + path, e);
        }
    }

    public synchronized void restore(Collection<MeshDevice> devices) {
        if (!Files.exists(path)) {
            return;
        }
        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            Object raw = in.readObject();
            if (!(raw instanceof Map<?, ?> saved)) {
                throw new MeshException("Mesh file " + path + " did not contain a packet map", null);
            }
            int restored = 0;
            for (Map.Entry<?, ?> entry : saved.entrySet()) {
                MeshDevice device = find(devices, String.valueOf(entry.getKey()));
                if (device == null || !(entry.getValue() instanceof Iterable<?> packets)) {
                    continue;
                }
                for (Object item : packets) {
                    if (item instanceof MeshPacket packet) {
                        device.hold(packet);
                        restored++;
                    }
                }
            }
            log.info("Restored {} mesh packet(s) from {}", restored, path);
        } catch (MeshException e) {
            log.warn("Could not read mesh packet file {}: {}", path, e.getMessage());
        } catch (Exception e) {
            log.warn("Could not read mesh packet file {}: {}", path, e.getMessage());
        }
    }

    private static MeshDevice find(Collection<MeshDevice> devices, String id) {
        for (MeshDevice device : devices) {
            if (device.getDeviceId().equals(id)) {
                return device;
            }
        }
        return null;
    }

    public synchronized void clear() {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not delete mesh packet file {}: {}", path, e.getMessage());
        }
    }
}

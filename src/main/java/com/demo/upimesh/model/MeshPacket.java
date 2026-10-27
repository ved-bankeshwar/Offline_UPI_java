package com.demo.upimesh.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * The over-the-wire format. This is what hops from phone to phone via Bluetooth.
 *
 * The intermediate phones can read the OUTER fields (packetId, ttl, createdAt)
 * because they need them for routing and dedup. They CANNOT read `ciphertext` —
 * that's encrypted with the server's public key.
 *
 * NOTE on outer-field tampering:
 *   A malicious intermediate could change `packetId` or `createdAt`. That's why
 *   we use the ciphertext's hash (not packetId) as the idempotency key on the
 *   server. The ciphertext is authenticated by hybrid encryption, so any
 *   tampering inside the encrypted blob is detected on decryption.
 */
public class MeshPacket implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank
    private String packetId; // UUID, used by intermediates for gossip dedup

    @Min(0)
    private int ttl; // Hops remaining; intermediates decrement it

    @NotNull
    private Long createdAt; // epoch millis, when sender created the packet

    @NotBlank
    private String ciphertext; // base64(RSA-encrypted AES key + AES-GCM ciphertext)

    public MeshPacket() {}

    public String getPacketId() { return packetId; }
    public void setPacketId(String packetId) { this.packetId = packetId; }

    public int getTtl() { return ttl; }
    public void setTtl(int ttl) { this.ttl = ttl; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public String getCiphertext() { return ciphertext; }
    public void setCiphertext(String ciphertext) { this.ciphertext = ciphertext; }

    /** One hop of this packet: can it move, and what does the next copy look like. */
    public Hop nextHop() {
        return new Hop();
    }

    public class Hop {
        public boolean canForward() {
            Integer hopsLeft = ttl;
            return hopsLeft != null && hopsLeft.compareTo(0) > 0;
        }

        public MeshPacket forward() {
            MeshPacket copy = new MeshPacket();
            copy.setPacketId(packetId);
            copy.setTtl(ttl - 1);
            copy.setCreatedAt(createdAt);
            copy.setCiphertext(ciphertext);
            return copy;
        }
    }
}

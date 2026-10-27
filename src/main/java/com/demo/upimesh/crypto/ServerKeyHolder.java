package com.demo.upimesh.crypto;

import com.demo.upimesh.io.MeshPacketFileStore;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.interfaces.RSAPrivateCrtKey;
import java.util.Base64;

/**
 * The server's RSA key. Packets on disk were encrypted with this key, so the
 * same key is loaded on the next start. A brand-new key cannot read those
 * packets, so the mesh file is cleared in that case.
 */
@Component
public class ServerKeyHolder {

    private static final Logger log = LoggerFactory.getLogger(ServerKeyHolder.class);

    private final Path keyFile;
    private final MeshPacketFileStore packetFiles;
    private KeyPair keyPair;

    public ServerKeyHolder(MeshPacketFileStore packetFiles,
                           @Value("${upi.mesh.key-file:data/keys/server.p8}") String keyFile) {
        this.packetFiles = packetFiles;
        this.keyFile = Path.of(keyFile);
    }

    @PostConstruct
    public void init() throws Exception {
        if (Files.exists(keyFile)) {
            byte[] encoded = Files.readAllBytes(keyFile);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(encoded));
            RSAPrivateCrtKey crt = (RSAPrivateCrtKey) privateKey;
            PublicKey publicKey = factory.generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
            this.keyPair = new KeyPair(publicKey, privateKey);
            log.info("Loaded server RSA key from {}", keyFile);
            return;
        }

        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        this.keyPair = gen.generateKeyPair();
        Path parent = keyFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(keyFile, keyPair.getPrivate().getEncoded());
        packetFiles.clear();
        log.info("New server RSA key saved to {}. Old mesh packets were cleared because they used another key.", keyFile);
    }

    public PublicKey getPublicKey() {
        return keyPair.getPublic();
    }

    public PrivateKey getPrivateKey() {
        return keyPair.getPrivate();
    }

    public String getPublicKeyBase64() {
        return Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    }
}

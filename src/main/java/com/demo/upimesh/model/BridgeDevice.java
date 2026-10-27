package com.demo.upimesh.model;

/** A phone that can reach the server. Flush uploads only this kind of phone. */
@MeshNode(internet = true)
public class BridgeDevice extends MeshDevice {

    public BridgeDevice(String deviceId) {
        super(deviceId);
    }
}

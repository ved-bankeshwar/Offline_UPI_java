package com.demo.upimesh.model;

/** A phone with no internet. It can only pass packets to neighbours. */
@MeshNode(internet = false)
public class OfflineDevice extends MeshDevice {

    public OfflineDevice(String deviceId) {
        super(deviceId);
    }
}

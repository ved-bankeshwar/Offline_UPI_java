package com.demo.upimesh.model;

/**
 * Which phones can hear each other. inRange[from][to] is a 2D array.
 * Every phone can hear every other phone, and nobody hears themselves.
 * Gossip reads this table instead of hard-coding "send to everyone".
 */
public class MeshTopology {

    private final String[] phoneIds;
    private final boolean[][] inRange;

    public MeshTopology(String[] phoneIds) {
        this.phoneIds = phoneIds;
        int n = phoneIds.length;
        inRange = new boolean[n][n];
        for (int from = 0; from < n; from++) {
            for (int to = 0; to < n; to++) {
                inRange[from][to] = from != to;
            }
        }
    }

    public int size() {
        return phoneIds.length;
    }

    public String phoneId(int index) {
        return phoneIds[index];
    }

    public boolean inRange(int from, int to) {
        return inRange[from][to];
    }

    public boolean[] neighbours(int from) {
        return inRange[from];
    }
}

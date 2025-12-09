package com.matter.bridge.app;

public class ClusterAttribute {
    public int clusterId;
    public int attributeId;
    public int type;
    public int size;
    public int mask;

    public ClusterAttribute(int clusterId, int attributeId, int type, int size, int mask) {
        this.clusterId = clusterId;
        this.attributeId = attributeId;
        this.type = type;
        this.size = size;
        this.mask = mask;
    }
}

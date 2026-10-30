package com.acme.scaffold.realtime.gateway;

import java.util.ArrayList;
import java.util.List;

/** 测试用假通道：无需真实 socket，记录写入的帧与关闭动作。 */
public final class FakeConnChannel implements ConnChannel {

    private final String connId;
    private final String userId;
    private final List<byte[]> written = new ArrayList<>();
    private int closeCode = -1;
    private String closeReason;
    private boolean active = true;
    private int inflightValue = 0;
    private long dropped = 0;

    public FakeConnChannel(String connId, String userId) {
        this.connId = connId;
        this.userId = userId;
    }

    @Override
    public String connId() {
        return connId;
    }

    @Override
    public String userId() {
        return userId;
    }

    @Override
    public void writeFrame(byte[] frame) {
        written.add(frame.clone());
    }

    @Override
    public void close(int code, String reason) {
        this.closeCode = code;
        this.closeReason = reason;
        this.active = false;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public int inflight() {
        return inflightValue;
    }

    public void setInflight(int value) {
        this.inflightValue = value;
    }

    @Override
    public long droppedCount() {
        return dropped;
    }

    @Override
    public void markDroppedOldest() {
        dropped++;
    }

    @Override
    public int closeCode() {
        return closeCode;
    }

    public List<byte[]> writtenFrames() {
        return written;
    }

    public String closeReason() {
        return closeReason;
    }

    public long droppedTotal() {
        return dropped;
    }
}

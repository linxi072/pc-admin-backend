package com.acme.scaffold.realtime.gateway;

/** 测试用手动时钟：按步长推进，断言不依赖真实时间。 */
public final class MutableClock implements Clock {

    private long now;

    public MutableClock(long startMillis) {
        this.now = startMillis;
    }

    public MutableClock() {
        this(1_700_000_000_000L);
    }

    @Override
    public long nowMillis() {
        return now;
    }

    public MutableClock advanceBy(long millis) {
        if (millis < 0) {
            throw new IllegalArgumentException("时间不能倒流: " + millis);
        }
        this.now += millis;
        return this;
    }

    public MutableClock setTo(long millis) {
        this.now = millis;
        return this;
    }
}

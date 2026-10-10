package com.acme.scaffold.monitor.health;

/**
 * 数据库可用性探测抽象。解耦 readiness 检查与具体数据源实现，便于单测中以桩替换。
 */
@FunctionalInterface
public interface DatabaseProbe {

    /**
     * @return 数据库当前可用返回 true，否则 false
     */
    boolean isAvailable();
}

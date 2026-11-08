package com.acme.scaffold.mybatis.dsl;

import java.util.List;
import java.util.Map;

/** 2 列投影结果；valueN() 按 SELECT 顺序取值。 */
public class Record2<T1, T2> extends Record {

    public Record2(Map<String, Object> raw, List<String> orderedKeys, Class<?>[] types) {
        super(raw, orderedKeys, types);
    }

    public Record2(Map<String, Object> raw, List<String> orderedKeys) {
        super(raw, orderedKeys);
    }

    @SuppressWarnings("unchecked")
    public T1 value1() {
        return (T1) typedValue(0);
    }

    @SuppressWarnings("unchecked")
    public T2 value2() {
        return (T2) typedValue(1);
    }

}

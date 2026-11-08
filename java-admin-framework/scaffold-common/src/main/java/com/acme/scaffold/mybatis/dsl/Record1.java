package com.acme.scaffold.mybatis.dsl;

import java.util.List;
import java.util.Map;

/** 1 列投影结果；valueN() 按 SELECT 顺序取值。 */
public class Record1<T1> extends Record {

    public Record1(Map<String, Object> raw, List<String> orderedKeys, Class<?>[] types) {
        super(raw, orderedKeys, types);
    }

    public Record1(Map<String, Object> raw, List<String> orderedKeys) {
        super(raw, orderedKeys);
    }

    @SuppressWarnings("unchecked")
    public T1 value1() {
        return (T1) typedValue(0);
    }

}

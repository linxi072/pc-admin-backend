package com.acme.scaffold.mybatis.dsl;

import java.util.List;
import java.util.Map;

/** 6 列投影结果；valueN() 按 SELECT 顺序取值。 */
public class Record6<T1, T2, T3, T4, T5, T6> extends Record {

    public Record6(Map<String, Object> raw, List<String> orderedKeys, Class<?>[] types) {
        super(raw, orderedKeys, types);
    }

    public Record6(Map<String, Object> raw, List<String> orderedKeys) {
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

    @SuppressWarnings("unchecked")
    public T3 value3() {
        return (T3) typedValue(2);
    }

    @SuppressWarnings("unchecked")
    public T4 value4() {
        return (T4) typedValue(3);
    }

    @SuppressWarnings("unchecked")
    public T5 value5() {
        return (T5) typedValue(4);
    }

    @SuppressWarnings("unchecked")
    public T6 value6() {
        return (T6) typedValue(5);
    }

}

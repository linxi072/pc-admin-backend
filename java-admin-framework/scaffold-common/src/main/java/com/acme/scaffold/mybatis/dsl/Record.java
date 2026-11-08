package com.acme.scaffold.mybatis.dsl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 一行结果：按列名（大小写不敏感）与按投影位置两种取数方式。 */
public class Record {

    private final Map<String, Object> byName = new LinkedHashMap<>();
    private final List<String> keys;
    private final Class<?>[] types;

    public Record(Map<String, Object> raw) {
        this(raw, raw == null ? new ArrayList<>() : new ArrayList<>(raw.keySet()), null);
    }

    public Record(Map<String, Object> raw, List<String> orderedKeys) {
        this(raw, orderedKeys, null);
    }

    public Record(Map<String, Object> raw, List<String> orderedKeys, Class<?>[] types) {
        if (raw != null) {
            for (Map.Entry<String, Object> e : raw.entrySet()) {
                byName.put(normalize(e.getKey()), e.getValue());
            }
        }
        this.keys = orderedKeys == null ? new ArrayList<>() : new ArrayList<>(orderedKeys);
        this.types = types;
    }

    private static String normalize(String key) {
        return key == null ? "" : key.toLowerCase(java.util.Locale.ROOT);
    }

    public Map<String, Object> intoMap() {
        return byName;
    }

    public int size() {
        return byName.size();
    }

    public Field<?> field(String name) {
        return byName.containsKey(normalize(name)) ? new Field<>(name, Object.class) : null;
    }

    public Object get(String name) {
        return byName.get(normalize(name));
    }

    public <T> T get(String name, Class<T> type) {
        return convert(byName.get(normalize(name)), type);
    }

    public <T> T get(Field<T> field) {
        return convert(byName.get(normalize(field.sql())), field.getType());
    }

    public Object get(int index) {
        return valueAt(index);
    }

    public <T> T get(int index, Class<T> type) {
        return convert(valueAt(index), type);
    }

    public Object valueAt(int index) {
        if (index < 0 || index >= keys.size()) {
            return null;
        }
        return byName.get(normalize(keys.get(index)));
    }

    @SuppressWarnings("unchecked")
    protected <X> X typedValue(int index) {
        Object value = valueAt(index);
        Class<?> t = types != null && index < types.length ? types[index] : null;
        return (X) (t == null ? value : convert(value, t));
    }

    @SuppressWarnings("unchecked")
    public static <T> T convert(Object value, Class<T> targetType) {
        if (value == null || targetType == null || targetType == Object.class) {
            return (T) value;
        }
        if (targetType.isInstance(value)) {
            return (T) value;
        }
        if (targetType == String.class) {
            return (T) (value instanceof JSON j ? j.data() : value.toString());
        }
        if ((targetType == LocalDateTime.class) && value instanceof Timestamp t) {
            return (T) t.toLocalDateTime();
        }
        if ((targetType == LocalDateTime.class) && value instanceof Date d) {
            return (T) LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        }
        if ((targetType == LocalDate.class) && value instanceof Date d) {
            return (T) LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()).toLocalDate();
        }
        if (value instanceof Number n) {
            if (targetType == Long.class || targetType == long.class) return (T) Long.valueOf(n.longValue());
            if (targetType == Integer.class || targetType == int.class) return (T) Integer.valueOf(n.intValue());
            if (targetType == Short.class || targetType == short.class) return (T) Short.valueOf(n.shortValue());
            if (targetType == Byte.class || targetType == byte.class) return (T) Byte.valueOf(n.byteValue());
            if (targetType == Double.class || targetType == double.class) return (T) Double.valueOf(n.doubleValue());
            if (targetType == Float.class || targetType == float.class) return (T) Float.valueOf(n.floatValue());
            if (targetType == BigDecimal.class) return (T) BigDecimal.valueOf(n.doubleValue());
            if (targetType == BigInteger.class) return (T) BigInteger.valueOf(n.longValue());
            if (targetType == Boolean.class || targetType == boolean.class) return (T) Boolean.valueOf(n.intValue() != 0);
        }
        if (value instanceof Boolean b) {
            if (targetType == Integer.class || targetType == int.class) return (T) Integer.valueOf(b ? 1 : 0);
            if (targetType == Long.class || targetType == long.class) return (T) Long.valueOf(b ? 1L : 0L);
            if (targetType == Boolean.class || targetType == boolean.class) return (T) b;
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof String s) {
                return (T) Boolean.valueOf("1".equals(s) || "true".equalsIgnoreCase(s) || "Y".equalsIgnoreCase(s));
            }
        }
        return (T) value;
    }
}

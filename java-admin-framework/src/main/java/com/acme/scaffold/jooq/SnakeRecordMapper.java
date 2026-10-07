package com.acme.scaffold.jooq;

import org.jooq.Field;
import org.jooq.JSON;
import org.jooq.Record;
import org.jooq.RecordMapper;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * 将 jOOQ {@link Record} 映射到普通 POJO（如原 *DO）。
 * 列名采用 snake_case，POJO 属性为 camelCase，按名称（忽略下划线）匹配。
 */
public class SnakeRecordMapper<E> implements RecordMapper<Record, E> {

    private final Class<E> type;

    public SnakeRecordMapper(Class<E> type) {
        this.type = type;
    }

    @Override
    public E map(Record record) {
        try {
            E target = type.getDeclaredConstructor().newInstance();
            for (PropertyDescriptor pd : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
                String prop = pd.getName();
                if ("class".equals(prop)) {
                    continue;
                }
                Method setter = pd.getWriteMethod();
                if (setter == null) {
                    continue;
                }
                Field<?> field = record.field(toSnake(prop));
                if (field == null) {
                    continue;
                }
                Object value = record.get(field);
                if (value == null) {
                    continue;
                }
                Class<?> paramType = setter.getParameterTypes()[0];
                setter.invoke(target, convert(value, paramType));
            }
            return target;
        } catch (ReflectiveOperationException | IntrospectionException e) {
            throw new IllegalStateException("映射 Record -> " + type.getName() + " 失败", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Object convert(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        // 时间类型转换
        if (targetType == LocalDateTime.class && value instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        if (targetType == LocalDateTime.class && value instanceof Date d) {
            return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault());
        }
        if (targetType == LocalDate.class && value instanceof Date d) {
            return LocalDateTime.ofInstant(d.toInstant(), ZoneId.systemDefault()).toLocalDate();
        }
        // 数值类型转换
        if (value instanceof Number n) {
            if (targetType == Long.class || targetType == long.class) {
                return n.longValue();
            }
            if (targetType == Integer.class || targetType == int.class) {
                return n.intValue();
            }
            if (targetType == Short.class || targetType == short.class) {
                return n.shortValue();
            }
            if (targetType == Byte.class || targetType == byte.class) {
                return n.byteValue();
            }
            if (targetType == Double.class || targetType == double.class) {
                return n.doubleValue();
            }
            if (targetType == Float.class || targetType == float.class) {
                return n.floatValue();
            }
            if (targetType == BigDecimal.class) {
                return BigDecimal.valueOf(n.doubleValue());
            }
            if (targetType == BigInteger.class) {
                return BigInteger.valueOf(n.longValue());
            }
            if (targetType == Boolean.class || targetType == boolean.class) {
                return n.intValue() != 0;
            }
        }
        if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Boolean b) {
                return b;
            }
            if (value instanceof Number n) {
                return n.intValue() != 0;
            }
            if (value instanceof String s) {
                return "1".equals(s) || "true".equalsIgnoreCase(s) || "Y".equalsIgnoreCase(s);
            }
        }
        // JSON 列（如 sys_operation_log.request_summary）由 jOOQ 映射为 org.jooq.JSON，
        // 直接塞进 String 字段会抛 argument type mismatch，这里统一取文本。
        if (targetType == String.class && !(value instanceof String)) {
            return value instanceof JSON json ? json.data() : value.toString();
        }
        return value;
    }

    static String toSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) {
                if (sb.length() > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}

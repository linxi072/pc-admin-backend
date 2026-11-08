package com.acme.scaffold.mybatis.dsl;

/** 数据访问异常，替代原 org.jooq.exception.DataAccessException。 */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}

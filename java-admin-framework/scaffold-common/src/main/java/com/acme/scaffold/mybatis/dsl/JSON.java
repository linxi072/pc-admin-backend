package com.acme.scaffold.mybatis.dsl;

/** JSON 列值的轻量包装，保持与 SnakeRecordMapper 的 JSON 取值语义一致。 */
public class JSON {

    private final String data;

    public JSON(String data) {
        this.data = data;
    }

    public String data() {
        return data;
    }

    @Override
    public String toString() {
        return data;
    }
}

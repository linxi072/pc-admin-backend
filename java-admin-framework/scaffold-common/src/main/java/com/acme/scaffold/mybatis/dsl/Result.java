package com.acme.scaffold.mybatis.dsl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** 查询结果集。 */
public class Result<R extends Record> extends ArrayList<R> {

    public Result() {
        super();
    }

    public Result(Collection<? extends R> rows) {
        super(rows);
    }

    /** 兼容 jOOQ Result.map(RecordMapper)。 */
    public <E> List<E> map(RecordMapper<R, E> mapper) {
        List<E> out = new ArrayList<>();
        for (R row : this) {
            E mapped = mapper.map(row);
            if (mapped != null) {
                out.add(mapped);
            }
        }
        return out;
    }
}

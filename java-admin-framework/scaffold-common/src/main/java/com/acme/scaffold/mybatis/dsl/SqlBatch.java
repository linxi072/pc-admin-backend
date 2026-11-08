package com.acme.scaffold.mybatis.dsl;

import java.util.ArrayList;
import java.util.List;

/** 批量执行包装：逐条执行同一批语句，语义等价于 dsl.batch(queries).execute()。 */
public class SqlBatch {

    private final List<Query> queries;

    public SqlBatch(List<Query> queries) {
        this.queries = queries == null ? new ArrayList<>() : queries;
    }

    public int[] execute() {
        int[] result = new int[queries.size()];
        for (int i = 0; i < queries.size(); i++) {
            result[i] = queries.get(i).execute();
        }
        return result;
    }
}

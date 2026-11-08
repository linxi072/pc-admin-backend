package com.acme.scaffold.mybatis.dsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一的动态 SQL 语句对象：同时扮演 SELECT / INSERT / UPDATE / DELETE / 子查询的各个步骤。
 *
 * <p>调用方只拼接「表达式 + 绑定值」，本类负责渲染最终 SQL 与参数顺序，
 * 再由 {@link DynamicMapper} 通过 MyBatis SqlSession 执行。</p>
 */
public class SqlStatement<R extends Record> implements Select<R>, SelectConditionStep<R>, SelectSeekStepN<R>,
        Query, Iterable<R> {

    private final DSLContext ctx;
    private final Class<?> rowType;

    private String kind = "select";
    private final List<Field<?>> projection = new ArrayList<>();
    private boolean distinct;
    private String from;
    private final List<Condition> where = new ArrayList<>();
    private final List<String> joins = new ArrayList<>();
    private final List<Object> joinBinds = new ArrayList<>();
    private Table<?> pendingJoin;
    private String pendingJoinPrefix = "JOIN";
    private final List<Field<?>> groupBy = new ArrayList<>();
    private final List<Condition> having = new ArrayList<>();
    private final List<OrderField<?>> orderBy = new ArrayList<>();
    private int limit = -1;
    private long offset = -1;
    private final LinkedHashMap<Field<?>, Object> sets = new LinkedHashMap<>();
    private final List<Object> insertValues = new ArrayList<>();
    private String rawSql;
    private final List<Object> rawBinds = new ArrayList<>();

    public SqlStatement(DSLContext ctx, Class<?> rowType) {
        this.ctx = ctx;
        this.rowType = rowType == null ? Record.class : rowType;
    }

    // ---------------- 构造（供 DSLContext / DSL 调用） ----------------

    SqlStatement<R> select(Field<?>... fields) {
        kind = "select";
        projection.clear();
        if (fields != null) {
            projection.addAll(Arrays.asList(fields));
        }
        return this;
    }

    SqlStatement<R> select(Collection<? extends Field<?>> fields) {
        kind = "select";
        projection.clear();
        if (fields != null) {
            projection.addAll(fields);
        }
        return this;
    }

    SqlStatement<R> projection(String sql, Class<?> type) {
        projection.clear();
        projection.add(new Field<>(sql, type));
        return this;
    }

    SqlStatement<R> selectOne() {
        return projection("1", Integer.class);
    }

    @Override
    public SqlStatement<R> from(Table<?> table) {
        this.from = table.renderFrom();
        return this;
    }

    @Override
    public SqlStatement<R> from(String table) {
        this.from = table;
        return this;
    }

    void configure(String queryKind, String table) {
        this.kind = queryKind;
        this.from = table;
    }

    void setDistinct(boolean value) {
        this.distinct = value;
    }

    void setRaw(String sql, Object... binds) {
        this.kind = "raw";
        this.rawSql = sql;
        this.rawBinds.clear();
        if (binds != null) {
            this.rawBinds.addAll(Arrays.asList(binds));
        }
    }

    // ---------------- 链式 ----------------

    @Override
    public SqlStatement<R> where(Condition... conditions) {
        if (conditions != null) {
            for (Condition c : conditions) {
                if (c != null && !c.isEmpty()) {
                    where.add(c);
                }
            }
        }
        return this;
    }

    public SqlStatement<R> where(Collection<? extends Condition> conditions) {
        if (conditions != null) {
            for (Condition c : conditions) {
                if (c != null && !c.isEmpty()) {
                    where.add(c);
                }
            }
        }
        return this;
    }

    @Override
    public SqlStatement<R> and(Condition condition) {
        return where(condition);
    }

    @Override
    public SqlStatement<R> or(Condition condition) {
        if (condition == null || condition.isEmpty()) {
            return this;
        }
        Condition merged = where.isEmpty() ? condition : Condition.any(where).or(condition);
        where.clear();
        where.add(merged);
        return this;
    }

    @Override
    public SqlStatement<R> orderBy(OrderField<?>... fields) {
        if (fields != null) {
            orderBy.addAll(Arrays.asList(fields));
        }
        return this;
    }

    public SqlStatement<R> orderBy(Collection<? extends OrderField<?>> fields) {
        if (fields != null) {
            orderBy.addAll(fields);
        }
        return this;
    }

    @Override
    public SqlStatement<R> groupBy(Field<?>... fields) {
        if (fields != null) {
            groupBy.addAll(Arrays.asList(fields));
        }
        return this;
    }

    @Override
    public SqlStatement<R> having(Condition condition) {
        if (condition != null && !condition.isEmpty()) {
            having.add(condition);
        }
        return this;
    }

    @Override
    public SqlStatement<R> limit(int rows) {
        this.limit = rows;
        return this;
    }

    @Override
    public SqlStatement<R> offset(long rows) {
        this.offset = rows;
        return this;
    }

    public SqlStatement<R> offset(int rows) {
        return offset((long) rows);
    }

    @Override
    public SqlStatement<R> join(Table<?> table) {
        pendingJoin = table;
        pendingJoinPrefix = "JOIN";
        return this;
    }

    public SqlStatement<R> join(String table) {
        return join(new Table<>(table));
    }

    @Override
    public SqlStatement<R> leftJoin(Table<?> table) {
        pendingJoin = table;
        pendingJoinPrefix = "LEFT JOIN";
        return this;
    }

    public SqlStatement<R> leftJoin(String table) {
        return leftJoin(new Table<>(table));
    }

    @Override
    public SqlStatement<R> on(Condition condition) {
        if (pendingJoin != null && condition != null) {
            joins.add(pendingJoinPrefix + " " + pendingJoin.renderFrom() + " ON " + condition.sql());
            joinBinds.addAll(condition.binds());
            pendingJoin = null;
        }
        return this;
    }

    public SqlStatement<R> on(Field<?> left, Field<?> right) {
        return on(new Condition(left.sql() + " = " + right.sql(), SqlFragment.concat(left, right)));
    }

    /** keyset 游标：按 orderBy 的列与方向构造「字典序大于/小于游标」的条件。 */
    @Override
    public SqlStatement<R> seek(Object... values) {
        if (values == null || values.length == 0 || orderBy.isEmpty()) {
            return this;
        }
        List<String> cols = new ArrayList<>();
        List<Boolean> asc = new ArrayList<>();
        for (OrderField<?> o : orderBy) {
            String s = o.sql();
            boolean a = !s.toUpperCase().endsWith(" DESC");
            cols.add(s.replaceAll("(?i)\s+(ASC|DESC)$", "").trim());
            asc.add(a);
        }
        int n = Math.min(values.length, cols.size());
        Condition seekCond = Condition.NONE;
        for (int i = 0; i < n; i++) {
            StringBuilder part = new StringBuilder();
            List<Object> binds = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                part.append(cols.get(j)).append(" = ? AND ");
                binds.add(values[j]);
            }
            part.append(cols.get(i)).append(asc.get(i) ? " > ?" : " < ?");
            binds.add(values[i]);
            seekCond = seekCond.or(new Condition(part.toString(), binds));
        }
        if (!seekCond.isEmpty()) {
            where.add(new Condition("(" + seekCond.sql() + ")", new ArrayList<>(seekCond.binds)));
        }
        return this;
    }

    // ---------------- 写入 ----------------

    public SqlStatement<R> set(Map<Field<?>, Object> values) {
        if (values != null) {
            sets.putAll(values);
        }
        return this;
    }

    public <T> SqlStatement<R> set(Field<T> field, T value) {
        sets.put(field, value);
        return this;
    }

    public <T> SqlStatement<R> set(Field<T> field, Field<?> value) {
        sets.put(field, value);
        return this;
    }

    public SqlStatement<R> values(Object... values) {
        insertValues.clear();
        if (values != null) {
            insertValues.addAll(Arrays.asList(values));
        }
        return this;
    }

    public SqlStatement<R> returning(Field<?>... fields) {
        return this;
    }

    // ---------------- 渲染 ----------------

    public String renderSql() {
        if ("raw".equals(kind)) {
            return rawSql == null ? "" : rawSql;
        }
        StringBuilder sb = new StringBuilder();
        if ("insert".equals(kind)) {
            sb.append("INSERT INTO ").append(from);
            if (!sets.isEmpty()) {
                sb.append(" (");
                int i = 0;
                for (Field<?> f : sets.keySet()) {
                    if (i++ > 0) sb.append(", ");
                    sb.append(f.sql());
                }
                sb.append(") VALUES (");
                for (i = 0; i < sets.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append('?');
                }
                sb.append(')');
            } else if (!insertValues.isEmpty()) {
                sb.append(" VALUES (");
                for (int i = 0; i < insertValues.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append('?');
                }
                sb.append(')');
            }
            return sb.toString();
        }
        if ("update".equals(kind)) {
            sb.append("UPDATE ").append(from).append(" SET ");
            int i = 0;
            for (Map.Entry<Field<?>, Object> e : sets.entrySet()) {
                if (i++ > 0) sb.append(", ");
                sb.append(e.getKey().sql()).append(" = ");
                if (e.getValue() instanceof Field<?> f && f.sql().startsWith("?")) {
                    sb.append(f.sql());
                } else if (e.getValue() instanceof Field<?> f) {
                    sb.append(f.sql());
                } else {
                    sb.append('?');
                }
            }
            appendWhere(sb);
            return sb.toString();
        }
        if ("delete".equals(kind)) {
            sb.append("DELETE FROM ").append(from);
            appendWhere(sb);
            return sb.toString();
        }
        // SELECT
        sb.append("SELECT ");
        if (distinct) sb.append("DISTINCT ");
        if (projection.isEmpty()) {
            sb.append('*');
        } else {
            for (int i = 0; i < projection.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(projection.get(i).renderProjection(i));
            }
        }
        if (from != null && !from.isEmpty()) {
            sb.append(" FROM ").append(from);
        }
        for (String j : joins) {
            sb.append(' ').append(j);
        }
        appendWhere(sb);
        if (!groupBy.isEmpty()) {
            sb.append(" GROUP BY ");
            for (int i = 0; i < groupBy.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(groupBy.get(i).sql());
            }
        }
        if (!having.isEmpty()) {
            sb.append(" HAVING ");
            for (int i = 0; i < having.size(); i++) {
                if (i > 0) sb.append(" AND ");
                sb.append('(').append(having.get(i).sql()).append(')');
            }
        }
        if (!orderBy.isEmpty()) {
            sb.append(" ORDER BY ");
            for (int i = 0; i < orderBy.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(orderBy.get(i).sql());
            }
        }
        if (limit >= 0) {
            sb.append(" LIMIT ").append(limit);
        }
        if (offset >= 0) {
            if (limit < 0) sb.append(" LIMIT 18446744073709551615");
            sb.append(" OFFSET ").append(offset);
        }
        return sb.toString();
    }

    private void appendWhere(StringBuilder sb) {
        if (where.isEmpty()) return;
        sb.append(" WHERE ");
        for (int i = 0; i < where.size(); i++) {
            if (i > 0) sb.append(" AND ");
            sb.append('(').append(where.get(i).sql()).append(')');
        }
    }

    public List<Object> renderBinds() {
        if ("raw".equals(kind)) {
            return new ArrayList<>(rawBinds);
        }
        List<Object> b = new ArrayList<>();
        if ("insert".equals(kind)) {
            if (!sets.isEmpty()) {
                for (Object v : sets.values()) {
                    if (v instanceof Field<?> f && f.sql().startsWith("?")) {
                        b.addAll(f.binds());
                    } else if (v instanceof Field<?> f && f.sql().contains("?")) {
                        b.addAll(f.binds());
                    } else {
                        b.add(v);
                    }
                }
            } else {
                b.addAll(insertValues);
            }
            return b;
        }
        if ("update".equals(kind)) {
            for (Object v : sets.values()) {
                if (v instanceof Field<?> f) b.addAll(f.binds());
                else b.add(v);
            }
            appendConditionBinds(b);
            return b;
        }
        if ("delete".equals(kind)) {
            appendConditionBinds(b);
            return b;
        }
        for (Field<?> f : projection) b.addAll(f.binds());
        b.addAll(joinBinds);
        appendConditionBinds(b);
        for (Field<?> f : groupBy) b.addAll(f.binds());
        for (Condition c : having) b.addAll(c.binds());
        for (OrderField<?> o : orderBy) b.addAll(o.binds());
        return b;
    }

    private void appendConditionBinds(List<Object> b) {
        for (Condition c : where) b.addAll(c.binds());
    }

    private List<String> resultKeys() {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < projection.size(); i++) {
            keys.add(projection.get(i).resultKey(i));
        }
        return keys;
    }

    private Class<?>[] projectionTypes() {
        Class<?>[] types = new Class<?>[projection.size()];
        for (int i = 0; i < projection.size(); i++) {
            types[i] = projection.get(i).getType();
        }
        return types;
    }

    @SuppressWarnings("unchecked")
    private R newRecord(Map<String, Object> row) {
        List<String> keys = projection.isEmpty()
                ? (row == null ? new ArrayList<>() : new ArrayList<>(row.keySet()))
                : resultKeys();
        Class<?>[] types = projectionTypes();
        if (rowType == Record1.class) return (R) new Record1<>(row, keys, types);
        if (rowType == Record2.class) return (R) new Record2<>(row, keys, types);
        if (rowType == Record3.class) return (R) new Record3<>(row, keys, types);
        if (rowType == Record4.class) return (R) new Record4<>(row, keys, types);
        if (rowType == Record5.class) return (R) new Record5<>(row, keys, types);
        if (rowType == Record6.class) return (R) new Record6<>(row, keys, types);
        return (R) new Record(row, keys, types);
    }

    // ---------------- 执行 ----------------

    private DSLContext requireContext() {
        if (ctx == null) {
            throw new IllegalStateException("该查询由 DSL 静态工厂构造，仅可作子查询渲染，不能直接执行");
        }
        return ctx;
    }

    @Override
    public Result<R> fetch() {
        DSLContext c = requireContext();
        String sql = renderSql();
        List<Object> binds = renderBinds();
        c.fireStart(sql);
        try {
            List<Map<String, Object>> rows = c.mapper().select(new DynamicStatement(sql, binds));
            Result<R> out = new Result<>();
            if (rows != null) {
                for (Map<String, Object> row : rows) {
                    out.add(newRecord(row));
                }
            }
            return out;
        } finally {
            c.fireEnd(sql);
        }
    }

    @Override
    public <E> List<E> fetch(RecordMapper<R, E> mapper) {
        List<E> out = new ArrayList<>();
        for (R row : fetch()) {
            E e = mapper.map(row);
            if (e != null) out.add(e);
        }
        return out;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <E> List<E> fetchInto(Class<E> type) {
        RecordMapper<Record, E> mapper = new com.acme.scaffold.mybatis.dsl.SnakeRecordMapper<>(type);
        List<E> out = new ArrayList<>();
        for (R row : fetch()) {
            out.add(mapper.map(row));
        }
        return out;
    }

    @Override
    public R fetchOne() {
        Result<R> rows = fetch();
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public <E> E fetchOne(RecordMapper<R, E> mapper) {
        R row = fetchOne();
        return row == null ? null : mapper.map(row);
    }

    @Override
    public <E> E fetchOneInto(Class<E> type) {
        List<E> rows = fetchInto(type);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public <T> T fetchOne(int index, Class<T> type) {
        R row = fetchOne();
        return row == null ? null : row.get(index, type);
    }

    @Override
    public int fetchCount() {
        DSLContext c = requireContext();
        String sql = "SELECT count(*) AS __mb0 FROM (" + renderSql() + ") __mb_cnt";
        List<Object> binds = renderBinds();
        Map<String, Object> row = c.mapper().selectOne(new DynamicStatement(sql, binds));
        if (row == null || row.isEmpty()) return 0;
        Object v = row.values().iterator().next();
        return v == null ? 0 : ((Number) v).intValue();
    }

    @Override
    public boolean fetchExists() {
        DSLContext c = requireContext();
        String sql = "SELECT 1 AS __mb0 FROM (" + renderSql() + ") __mb_ext LIMIT 1";
        List<Object> binds = renderBinds();
        return c.mapper().selectOne(new DynamicStatement(sql, binds)) != null;
    }

    @Override
    public int execute() {
        DSLContext c = requireContext();
        String sql = renderSql();
        List<Object> binds = renderBinds();
        c.fireStart(sql);
        try {
            DynamicStatement st = new DynamicStatement(sql, binds);
            if ("insert".equals(kind)) return c.mapper().insert(st);
            if ("delete".equals(kind)) return c.mapper().delete(st);
            return c.mapper().update(st);
        } finally {
            c.fireEnd(sql);
        }
    }

    @Override
    public String getSQL() {
        return renderSql();
    }

    /** 兼容 jOOQ ResultQuery 的可迭代语义：dsl.resultQuery(...).forEach(record -> ...)。 */
    @Override
    public java.util.Iterator<R> iterator() {
        return fetch().iterator();
    }

    @Override
    public void forEach(java.util.function.Consumer<? super R> action) {
        fetch().forEach(action);
    }
}

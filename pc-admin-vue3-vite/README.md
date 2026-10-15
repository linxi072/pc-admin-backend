# 运营管理后台前端（Vue3 + Element Plus + Vite）

基于 Vue 3 + Element Plus + Vite 的标准工程化后台管理前端，对接后端 `java-admin-framework`（jOOQ / JobRunr / Flowable）。覆盖 RBAC（用户/角色/菜单/接口资源）、工作流审批等模块，并实现列表/分页/搜索筛选/增删改查与角色权限树联动、用户硬删除、菜单·接口资源编辑等增强。

## 文档

前端接口规范、工程说明与页面开发指引已整合进后端《开发手册》：

- 开发手册（前端章节）：[`../java-admin-framework/docs/manual/development.md`](../java-admin-framework/docs/manual/development.md)
- 文档总目录：[`../java-admin-framework/docs/index.md`](../java-admin-framework/docs/index.md)

## 技术栈

Vue 3.4（`<script setup>`）· Vue Router 4 · Element Plus 2.8 · Axios（统一封装，自动注入 JWT、拆包 `Result<T>`、401 跳登录）· Vite 5（开发期 `/api` 代理）。

## 快速开始

```bash
npm install
npm run dev      # 开发（vite 代理 /api → http://127.0.0.1:8080）
npm run build    # 生产构建
```

> 接口契约、统一响应/分页封装、分层调用范式详见《开发手册》前端接口规范章节。

package com.acme.scaffold.codegen;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 代码生成器配置。
 *
 * <p>生成代码的包名前缀与作者信息由此处统一控制，避免在页面上反复输入。</p>
 */
@ConfigurationProperties(prefix = "codegen")
public class CodeGenProperties {

    /** 生成 Java 代码的包名前缀（实体类所在包的父级，如 com.acme.scaffold）。 */
    private String basePackage = "com.acme.scaffold";

    /** 默认作者（页面作者输入框为空时回退此值）。 */
    private String author = "scaffold";

    /** 前端源码相对路径提示（生成 Vue / api.js 的 path 前缀，仅作展示）。 */
    private String webPath = "pc-admin-vue3-vite/src";

    public String getBasePackage() {
        return basePackage;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getWebPath() {
        return webPath;
    }

    public void setWebPath(String webPath) {
        this.webPath = webPath;
    }
}

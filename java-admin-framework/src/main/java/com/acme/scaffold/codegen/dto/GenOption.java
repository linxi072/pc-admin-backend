package com.acme.scaffold.codegen.dto;

/**
 * 代码生成入参。
 *
 * <p>构造期 fail-fast：tableName / moduleName 必填；withFrontend 控制是否生成前端文件。</p>
 *
 * @param tableName    源表名（必填，如 sys_user）
 * @param moduleName   模块名（必填，用于包名与前端路径，如 user；仅允许小写字母/数字/连字符）
 * @param functionName 功能名（页面标题，如「用户管理」；为空时回退表注释或类名）
 * @param className    实体类名（为空时由表名自动推导，如 sys_user → User）
 * @param author       作者（写入文件头注释；为空时取配置默认值）
 * @param withFrontend 是否生成前端（Vue 页面 + api.js）
 */
public record GenOption(String tableName, String moduleName, String functionName,
                        String className, String author, boolean withFrontend) {

    private static final java.util.regex.Pattern MODULE = java.util.regex.Pattern.compile("[a-z][a-z0-9-]*");

    public GenOption {
        if (tableName == null || tableName.isBlank()) {
            throw new IllegalArgumentException("tableName 不能为空");
        }
        if (moduleName == null || moduleName.isBlank()) {
            throw new IllegalArgumentException("moduleName 不能为空");
        }
        if (!MODULE.matcher(moduleName.trim()).matches()) {
            throw new IllegalArgumentException("moduleName 仅允许小写字母/数字/连字符且以字母开头: " + moduleName);
        }
    }
}

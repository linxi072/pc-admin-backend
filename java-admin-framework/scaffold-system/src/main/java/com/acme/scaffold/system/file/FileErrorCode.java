package com.acme.scaffold.system.file;

import com.acme.scaffold.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * 文件管理业务错误码。命名规则：模块前缀 {@code FILE_} + 三位编号。
 */
public enum FileErrorCode implements ErrorCode {

    FILE_EMPTY("FILE_001", "请选择要上传的文件", HttpStatus.BAD_REQUEST.value()),
    FILE_TOO_LARGE("FILE_002", "文件大小超过限制", HttpStatus.BAD_REQUEST.value()),
    FILE_TYPE_NOT_ALLOWED("FILE_003", "不支持的文件类型", HttpStatus.BAD_REQUEST.value()),
    FILE_CONTENT_MISMATCH("FILE_004", "文件内容与扩展名不符", HttpStatus.BAD_REQUEST.value()),
    FILE_HASH_INVALID("FILE_005", "内容哈希格式不正确", HttpStatus.BAD_REQUEST.value()),
    FILE_BATCH_EMPTY("FILE_006", "请选择要删除的文件", HttpStatus.BAD_REQUEST.value()),
    FILE_BATCH_TOO_MANY("FILE_007", "单次批量删除数量超过上限", HttpStatus.BAD_REQUEST.value()),
    FILE_STORE_FAILED("FILE_500", "文件存储失败，请稍后重试", HttpStatus.INTERNAL_SERVER_ERROR.value()),
    FILE_NOT_FOUND("FILE_404", "文件不存在或已删除", HttpStatus.NOT_FOUND.value());

    private final String code;
    private final String message;
    private final int httpStatus;

    FileErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }

    @Override
    public int httpStatus() {
        return httpStatus;
    }
}

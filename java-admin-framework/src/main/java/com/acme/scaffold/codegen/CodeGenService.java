package com.acme.scaffold.codegen;

import com.acme.scaffold.codegen.dto.GenOption;
import com.acme.scaffold.codegen.dto.GeneratedFileVO;
import com.acme.scaffold.codegen.dto.TableMetaVO;
import com.acme.scaffold.common.error.CommonErrorCode;
import com.acme.scaffold.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码生成服务：拉取表结构 → 渲染后端六层与前端两文件 → 预览 / 打包下载。
 *
 * <p>生成结果为「文本预览」：文件是否落盘、落盘位置由开发者决定（页面提供复制与 zip 下载），
 * 服务端不写业务磁盘，避免生成器越权改动源码树。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGenService {

    private final TableSchemaService tableSchemaService;
    private final CodeGenProperties props;

    /** 生成预览文件列表（顺序：DO → VO → Query → Controller → Service → ServiceImpl → [Vue → api.js]）。 */
    public List<GeneratedFileVO> preview(GenOption option) {
        TableMetaVO meta = requireMeta(option.tableName());
        return CodeTemplates.renderAll(meta, option, props);
    }

    /** 打包为 zip（entry 使用建议落盘相对路径，保留目录结构）。 */
    public byte[] downloadZip(GenOption option) {
        List<GeneratedFileVO> files = preview(option);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (GeneratedFileVO f : files) {
                zos.putNextEntry(new ZipEntry(f.path()));
                zos.write(f.content().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        } catch (IOException e) {
            // 内存流写入不涉及真实 IO 故障，此处为防御性兜底
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, "打包失败: " + e.getMessage());
        }
        return bos.toByteArray();
    }

    /** zip 文件名（含表名，便于识别）。 */
    public String zipName(GenOption option) {
        return "codegen-" + option.tableName().trim() + ".zip";
    }

    private TableMetaVO requireMeta(String tableName) {
        TableMetaVO meta = tableSchemaService.tableMeta(tableName);
        if (meta == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "表不存在: " + tableName);
        }
        if (meta.columns().isEmpty()) {
            throw new BusinessException(CommonErrorCode.VALIDATION_ERROR, "表无字段: " + tableName);
        }
        return meta;
    }
}

package com.acme.scaffold.system.export;

import com.acme.scaffold.common.export.ExportTask;
import com.acme.scaffold.system.file.FileService;
import com.acme.scaffold.system.file.FileView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 文件管理列表导出（导出中心首个内置模板）。
 *
 * <p>复用 {@link FileService} 的数据查询（与「文件管理」页面条件一致），
 * 将 {@link FileView} 映射为 CSV 行，字段含来源功能模块与上传人，便于审计归集。
 */
@Component
@RequiredArgsConstructor
public class FileListExportTask implements ExportTask {

    private final FileService fileService;

    @Override
    public String code() {
        return "files";
    }

    @Override
    public String displayName() {
        return "文件管理列表";
    }

    @Override
    public String fileName() {
        return "file-list";
    }

    @Override
    public List<String> headers() {
        return List.of("ID", "文件名", "来源功能模块", "业务ID", "上传人",
                "大小(字节)", "类型", "下载次数", "上传时间");
    }

    @Override
    public List<List<String>> rows(Map<String, String> params) {
        String sourceModule = blankToNull(params.get("sourceModule"));
        String uploaderName = blankToNull(params.get("uploaderName"));
        String originalName = blankToNull(params.get("originalName"));
        List<FileView> list = fileService.listAll(sourceModule, uploaderName, originalName);
        return list.stream().map(v -> List.of(
                str(v.id()),
                str(v.originalName()),
                str(v.sourceModule()),
                str(v.bizId()),
                str(v.uploaderName()),
                str(v.fileSize()),
                str(v.contentType()),
                str(v.downloadCount()),
                str(v.createdAt())
        )).toList();
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}

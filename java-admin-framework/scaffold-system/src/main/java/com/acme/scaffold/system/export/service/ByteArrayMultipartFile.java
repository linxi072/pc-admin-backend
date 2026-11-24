package com.acme.scaffold.system.export.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

/** 把内存字节包装为 MultipartFile，供异步导出产物复用 FileStoragePort.store。 */
final class ByteArrayMultipartFile implements MultipartFile {

    private final byte[] content;
    private final String fileName;

    ByteArrayMultipartFile(byte[] content, String fileName) {
        this.content = content == null ? new byte[0] : content;
        this.fileName = fileName == null ? "export" : fileName;
    }

    @Override public String getName() { return "exportFile"; }
    @Override public String getOriginalFilename() { return fileName; }
    @Override public String getContentType() { return "application/octet-stream"; }
    @Override public boolean isEmpty() { return content.length == 0; }
    @Override public long getSize() { return content.length; }
    @Override public byte[] getBytes() { return content.clone(); }
    @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }

    @Override
    public void transferTo(File dest) throws IOException {
        java.nio.file.Files.write(dest.toPath(), content);
    }
}

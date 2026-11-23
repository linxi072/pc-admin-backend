package com.acme.scaffold.system.file.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.acme.scaffold.system.file.domain.repository.ObjectStoragePort;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储路由单测（E4）：无远端 Bean 时回退本地（零回归）；显式 remote 时切远端；
 * storagePath 本地为绝对路径、远端为逻辑 URI。
 */
class FileStorageRouterTest {

    /** 最小远端桩：只关心 backendId 与 storagePath 的路由判定，其余方法不影响本测试。 */
    private static final class FakeRemote implements ObjectStoragePort {
        @Override public String backendId() { return "s3"; }
        @Override public String store(MultipartFile file, String ext) { return "remote-key." + ext; }
        @Override public InputStream openStream(String storedName) {
            return new ByteArrayInputStream("x".getBytes());
        }
        @Override public Resource loadAsResource(String storedName) {
            return new ByteArrayResource("x".getBytes());
        }
        @Override public void delete(String storedName) { }
        @Override public boolean exists(String storedName) { return true; }
    }

    @Test
    void withoutRemoteBeanShouldUseLocal() {
        FileStorageService local = mock(FileStorageService.class);
        when(local.exists("x")).thenReturn(false);
        when(local.resolve("x")).thenReturn(java.nio.file.Paths.get("/uploads/x"));
        FileStorageRouter router = new FileStorageRouter(local, null, new MockEnvironment());
        assertEquals("local", router.backendId());
        assertFalse(router.exists("x"));
        assertEquals("/uploads/x", router.storagePath("x"));
    }

    @Test
    void remoteBeanButLocalBackendShouldStayLocal() {
        FileStorageService local = mock(FileStorageService.class);
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.file.storage-backend", "local");
        FileStorageRouter router = new FileStorageRouter(local, new FakeRemote(), env);
        assertEquals("local", router.backendId());
        assertFalse(router.exists("x")); // 使用 local mock（默认 false），未走远端
    }

    @Test
    void remoteBackendShouldUseRemotePort() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.file.storage-backend", "remote");
        FileStorageRouter router = new FileStorageRouter(null, new FakeRemote(), env);
        assertEquals("s3", router.backendId());
        assertTrue(router.exists("any"));
        assertEquals("remote://s3/any", router.storagePath("any"));
        assertTrue(router.loadAsResource("any").exists());
    }
}

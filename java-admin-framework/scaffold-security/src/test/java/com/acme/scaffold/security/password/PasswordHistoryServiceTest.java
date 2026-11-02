package com.acme.scaffold.security.password;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.acme.scaffold.security.jwt.JwtProperties;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordHistoryServiceTest {

    @Mock
    private PasswordHistoryDataPort port;
    @Mock
    private PasswordEncoder encoder;

    private final JwtProperties jwt = new JwtProperties(); // 默认 passwordHistoryLimit=5
    private PasswordHistoryService service;

    @BeforeEach
    void init() {
        service = new PasswordHistoryService(port, encoder, jwt);
    }

    @Test
    void record_insertsThenPrunesWithoutDeleteWhenUnderLimit() {
        service.record(7L, "enc");
        verify(port).insert(eq(7L), eq("enc"), any(LocalDateTime.class));
        verify(port).allRecords(7L);
        // 默认保留 5 条以内：allRecords 返回空 -> 不触发 delete
        verify(port, never()).delete(any());
    }

    @Test
    void record_prunesExcessAfterInsert() {
        // 7 条记录：默认保留上限 5 -> 裁剪最早的 2 条（id=1、2，按时间降序自旧到新返回）
        when(port.allRecords(7L)).thenReturn(List.of(
                new PasswordHistoryRecord(1L, LocalDateTime.of(2026, 1, 1, 0, 0)),
                new PasswordHistoryRecord(2L, LocalDateTime.of(2026, 2, 1, 0, 0)),
                new PasswordHistoryRecord(3L, LocalDateTime.of(2026, 3, 1, 0, 0)),
                new PasswordHistoryRecord(4L, LocalDateTime.of(2026, 4, 1, 0, 0)),
                new PasswordHistoryRecord(5L, LocalDateTime.of(2026, 5, 1, 0, 0)),
                new PasswordHistoryRecord(6L, LocalDateTime.of(2026, 6, 1, 0, 0)),
                new PasswordHistoryRecord(7L, LocalDateTime.of(2026, 7, 1, 0, 0))));
        service.record(7L, "enc");
        verify(port).insert(eq(7L), eq("enc"), any(LocalDateTime.class));
        // limit=5 -> 删除最早的 1、2
        verify(port).delete(1L);
        verify(port).delete(2L);
        verify(port, times(2)).delete(any());
    }

    @Test
    void isReused_trueWhenHashMatches() {
        when(port.recentHashes(7L, 5)).thenReturn(List.of("h1"));
        when(encoder.matches("raw", "h1")).thenReturn(true);
        assertTrue(service.isReused(7L, "raw"));
        verify(port).recentHashes(7L, 5);
    }

    @Test
    void isReused_falseWhenNoMatch() {
        when(port.recentHashes(7L, 5)).thenReturn(List.of("h1"));
        when(encoder.matches("raw", "h1")).thenReturn(false);
        assertFalse(service.isReused(7L, "raw"));
    }
}

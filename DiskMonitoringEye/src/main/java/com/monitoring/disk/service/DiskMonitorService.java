package com.monitoring.disk.service;

import com.monitoring.disk.dto.DiskRawInfo;
import com.monitoring.disk.dto.DiskStatusDto;
import com.monitoring.disk.util.DiskMonitorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class DiskMonitorService {

    private static final String SEPARATOR = "=".repeat(60);
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${alert.threshold}")
    private double thresholdPercent;

    // ----------------------------------------------------------------
    // 스케줄러 (30초 주기 — 테스트용, 운영 전환 시 cron = "0 0 0/1 * * *" 으로 변경)
    // ----------------------------------------------------------------
    @Scheduled(cron = "0 0 0/1 * * *")
    public void monitorDiskSpace() {
        log.info("디스크 모니터링 시작");
        double usagePercent = DiskMonitorUtil.getDiskUsagePercentage();
        log.info("현재 디스크 사용률: {}%", String.format("%.2f", usagePercent));

        if (usagePercent >= thresholdPercent) {
            logWarning(usagePercent);
        }
    }

    // ----------------------------------------------------------------
    // API 제공용 — Controller 에서 호출
    // ----------------------------------------------------------------
    public DiskStatusDto getCurrentStatus() {
        DiskRawInfo raw = DiskMonitorUtil.getDiskRawInfo();
        double roundedUsage = Math.round(raw.usagePercentage() * 100.0) / 100.0;

        return new DiskStatusDto(
                raw.osName(),
                formatBytes(raw.totalBytes()),
                formatBytes(raw.usedBytes()),
                formatBytes(raw.freeBytes()),
                roundedUsage,
                LocalDateTime.now().format(FORMATTER)
        );
    }

    // ----------------------------------------------------------------
    // 내부 유틸
    // ----------------------------------------------------------------
    private void logWarning(double usagePercent) {
        String detectedAt = LocalDateTime.now().format(FORMATTER);
        log.warn(SEPARATOR);
        log.warn("!! [경고] 디스크 용량 임계치 초과 감지 !!");
        log.warn(SEPARATOR);
        log.warn("  감지 시간  : {}", detectedAt);
        log.warn("  임계치     : {}%", thresholdPercent);
        log.warn("  현재 사용률: {}%", String.format("%.2f", usagePercent));
        log.warn(SEPARATOR);
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024 * 1024 * 1024) {
            return String.format("%.1f TB", bytes / (1024.0 * 1024 * 1024 * 1024));
        } else if (bytes >= 1024L * 1024 * 1024) {
            return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        } else if (bytes >= 1024L * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        }
        return String.format("%d KB", bytes / 1024);
    }
}

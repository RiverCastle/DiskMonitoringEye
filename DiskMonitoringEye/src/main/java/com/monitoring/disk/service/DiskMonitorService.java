package com.monitoring.disk.service;

import com.monitoring.disk.dto.DiskRawInfo;
import com.monitoring.disk.dto.DiskStatusDto;
import com.monitoring.disk.dto.DriveInfo;
import com.monitoring.disk.util.DiskMonitorUtil;

import java.util.List;
import com.monitoring.disk.util.MonitoringConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
public class DiskMonitorService {

    private static final String SEPARATOR = "=".repeat(60);

    @Value("${alert.threshold}")
    private double thresholdPercent;

    // ----------------------------------------------------------------
    // 스케줄러 (1시간 주기)
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
                DiskMonitorUtil.formatBytes(raw.totalBytes()),
                DiskMonitorUtil.formatBytes(raw.usedBytes()),
                DiskMonitorUtil.formatBytes(raw.freeBytes()),
                roundedUsage,
                LocalDateTime.now().format(MonitoringConstants.DATETIME_FORMATTER)
        );
    }

    public List<DriveInfo> getAllDrives() {
        return DiskMonitorUtil.getAllDrives();
    }

    // ----------------------------------------------------------------
    // 내부 유틸
    // ----------------------------------------------------------------
    private void logWarning(double usagePercent) {
        String detectedAt = LocalDateTime.now().format(MonitoringConstants.DATETIME_FORMATTER);
        log.warn(SEPARATOR);
        log.warn("!! [경고] 디스크 용량 임계치 초과 감지 !!");
        log.warn(SEPARATOR);
        log.warn("  감지 시간  : {}", detectedAt);
        log.warn("  임계치     : {}%", thresholdPercent);
        log.warn("  현재 사용률: {}%", String.format("%.2f", usagePercent));
        log.warn(SEPARATOR);
    }

}

package com.monitoring.disk.dto;

/**
 * /api/monitor/disk API 응답 DTO
 */
public record DiskStatusDto(
        String osName,
        String totalSpace,
        String usedSpace,
        String freeSpace,
        double usagePercentage,
        String lastUpdated
) {}

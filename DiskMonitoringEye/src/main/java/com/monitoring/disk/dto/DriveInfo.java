package com.monitoring.disk.dto;

public record DriveInfo(
        String driveName,
        String totalSpace,
        String usedSpace,
        String freeSpace,
        double usagePercentage
) {}

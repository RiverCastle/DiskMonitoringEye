package com.monitoring.disk.dto;

public record DiskInfo(
        double usagePercentage,
        String totalSpace,
        String usedSpace,
        String freeSpace
) {}

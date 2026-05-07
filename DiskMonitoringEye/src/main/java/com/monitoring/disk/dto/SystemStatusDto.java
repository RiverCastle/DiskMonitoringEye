package com.monitoring.disk.dto;

public record SystemStatusDto(
        String os,
        CpuInfo cpu,
        MemoryInfo memory,
        DiskInfo disk,
        NetworkInfo network,
        String uptime,
        String timestamp
) {}

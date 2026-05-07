package com.monitoring.disk.dto;

public record MemoryInfo(long total, long used, long free, String unit, double percentage) {}

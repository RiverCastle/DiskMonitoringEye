package com.monitoring.disk.dto;

public record PortKillResult(
        int     port,
        long    pid,
        boolean success,
        String  message
) {}

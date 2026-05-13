package com.monitoring.disk.dto;

public record PortInfo(
        String protocol,
        int    localPort,
        String localAddress,
        String remoteAddress,
        String state,
        long   pid,
        String processName
) {}

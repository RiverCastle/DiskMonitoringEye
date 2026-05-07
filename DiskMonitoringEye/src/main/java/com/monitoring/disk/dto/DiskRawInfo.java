package com.monitoring.disk.dto;

/**
 * OS별 디스크 조회 결과를 담는 원시 데이터 레코드 (단위: bytes)
 */
public record DiskRawInfo(
        String osName,
        long   totalBytes,
        long   usedBytes,
        long   freeBytes,
        double usagePercentage
) {}

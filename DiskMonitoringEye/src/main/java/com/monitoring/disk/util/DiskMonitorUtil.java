package com.monitoring.disk.util;

import com.monitoring.disk.dto.DiskRawInfo;
import com.monitoring.disk.dto.DriveInfo;
import com.monitoring.disk.util.OSCommandExecutor.OsType;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DiskMonitorUtil {

    private DiskMonitorUtil() {}

    // ----------------------------------------------------------------
    // 퍼블릭 API
    // ----------------------------------------------------------------

    /** 사용률(%)만 필요한 스케줄러용 간편 메서드 */
    public static double getDiskUsagePercentage() {
        return getDiskRawInfo().usagePercentage();
    }

    /** OS를 판별하여 디스크 정보를 반환 (Windows: 가장 높은 사용률 드라이브 기준) */
    public static DiskRawInfo getDiskRawInfo() {
        String osName = System.getProperty("os.name");
        OsType osType = OSCommandExecutor.detectOs();
        log.info("OS 감지: {}", osName);

        try {
            return switch (osType) {
                case WINDOWS -> getWindowsDiskInfo(osName);
                case LINUX   -> getLinuxDiskInfo(osName);
                case SOLARIS -> getSolarisDiskInfo(osName);
                default      -> {
                    log.warn("지원하지 않는 OS입니다: {}", osName);
                    yield new DiskRawInfo(osName, 0L, 0L, 0L, 0.0);
                }
            };
        } catch (Exception e) {
            log.error("디스크 사용량 조회 중 오류 발생: {}", e.getMessage(), e);
        }
        return new DiskRawInfo(osName, 0L, 0L, 0L, 0.0);
    }

    /**
     * File.listRoots()로 시스템의 모든 드라이브를 탐색하여 DriveInfo 목록으로 반환한다.
     * - Windows: C:\, D:\, E:\ 등 마운트된 모든 로컬 드라이브
     * - Linux/Mac: 루트(/) 단일 항목
     * - 총 용량이 0인 드라이브(빈 CD-ROM 등)는 제외한다.
     */
    public static List<DriveInfo> getAllDrives() {
        List<DriveInfo> driveList = new ArrayList<>();
        File[] roots = File.listRoots();

        if (roots == null) return driveList;

        for (File root : roots) {
            if (!root.exists() || root.getTotalSpace() == 0) continue;

            long   totalBytes = root.getTotalSpace();
            // 네트워크/가상 드라이브는 getFreeSpace()가 하부 스토리지 기준으로 반환되어
            // totalBytes를 초과할 수 있으므로 상한을 totalBytes로 제한한다.
            long   freeBytes  = Math.min(root.getFreeSpace(), totalBytes);
            long   usedBytes  = totalBytes - freeBytes;
            double usage      = Math.round(((double) usedBytes / totalBytes) * 1000.0) / 10.0;

            log.info("[Drive] {} — 전체: {}, 사용: {}, 사용률: {}%",
                    root.getAbsolutePath(),
                    formatBytes(totalBytes), formatBytes(usedBytes),
                    String.format("%.1f", usage));

            if (usage >= 80.0) {
                log.warn("[Drive][경고] {} 사용률 {}% — 임계치(80%) 초과",
                        root.getAbsolutePath(), String.format("%.1f", usage));
            }

            driveList.add(new DriveInfo(
                    root.getAbsolutePath(),
                    formatBytes(totalBytes),
                    formatBytes(usedBytes),
                    formatBytes(freeBytes),
                    usage
            ));
        }
        return driveList;
    }

    // ----------------------------------------------------------------
    // OS별 내부 구현
    // ----------------------------------------------------------------

    /**
     * File.listRoots()로 모든 드라이브를 순회하며 80% 초과 시 경고 로그를 남기고,
     * 사용률이 가장 높은 드라이브를 시스템 상태 대표값으로 반환한다.
     */
    private static DiskRawInfo getWindowsDiskInfo(String osName) {
        File[] roots  = File.listRoots();
        DiskRawInfo highest = new DiskRawInfo(osName, 0L, 0L, 0L, 0.0);

        if (roots == null) return highest;

        for (File root : roots) {
            if (!root.exists() || root.getTotalSpace() == 0) continue;

            long   totalBytes = root.getTotalSpace();
            // 네트워크/가상 드라이브는 getFreeSpace()가 하부 스토리지 기준으로 반환되어
            // totalBytes를 초과할 수 있으므로 상한을 totalBytes로 제한한다.
            long   freeBytes  = Math.min(root.getFreeSpace(), totalBytes);
            long   usedBytes  = totalBytes - freeBytes;
            double usage      = ((double) usedBytes / totalBytes) * 100.0;

            log.info("[Windows] {} — 전체: {}GB, 사용: {}GB, 사용률: {}%",
                    root.getAbsolutePath(),
                    totalBytes / (1024L * 1024 * 1024),
                    usedBytes  / (1024L * 1024 * 1024),
                    String.format("%.2f", usage));

            if (usage >= 80.0) {
                log.warn("[Windows][경고] {} 사용률 {}% — 임계치(80%) 초과",
                        root.getAbsolutePath(), String.format("%.2f", usage));
            }

            if (usage > highest.usagePercentage()) {
                highest = new DiskRawInfo(osName, totalBytes, usedBytes, freeBytes, usage);
            }
        }
        return highest;
    }

    /** df -P: 컬럼 순서 — Filesystem | 1024-blocks | Used | Available | Capacity | Mounted */
    private static DiskRawInfo getLinuxDiskInfo(String osName) {
        String out = OSCommandExecutor.execute("df", "-P");
        String[] lines = out.split("\n");
        if (lines.length >= 2) {
            String line = lines[1].trim();
            if (!line.isEmpty()) {
                String[] parts = line.split("\\s+");
                long   totalKb = Long.parseLong(parts[1]);
                long   usedKb  = Long.parseLong(parts[2]);
                long   freeKb  = Long.parseLong(parts[3]);
                double usage   = Double.parseDouble(parts[4].replace("%", ""));

                log.info("[Linux/Mac] 전체: {}GB, 사용: {}KB, 여유: {}KB, 사용률: {}%",
                        totalKb / (1024 * 1024), usedKb, freeKb, usage);

                return new DiskRawInfo(osName,
                        totalKb * 1024L, usedKb * 1024L, freeKb * 1024L, usage);
            }
        }
        return new DiskRawInfo(osName, 0L, 0L, 0L, 0.0);
    }

    /**
     * df -k: 전체 파일시스템을 순회하며 capacity 80% 이상 항목에 경고 로그를 출력한다.
     * 대시보드 반환값은 실제 파일시스템 중 사용률이 가장 높은 항목을 기준으로 한다.
     * 컬럼 순서 — Filesystem | kbytes | used | avail | capacity | Mounted on
     */
    private static DiskRawInfo getSolarisDiskInfo(String osName) {
        String out = OSCommandExecutor.execute("df", "-k");
        String[] lines = out.split("\n");

        DiskRawInfo highest = new DiskRawInfo(osName, 0L, 0L, 0L, 0.0);

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            if (parts.length < 6) continue;

            String filesystem = parts[0];
            String mountPoint = parts[5];

            if (isSolarisVirtualFs(filesystem)) continue;

            try {
                double usage = Double.parseDouble(parts[4].replace("%", ""));

                log.info("[Solaris] {} ({}) — 사용률: {}%",
                        filesystem, mountPoint, String.format("%.2f", usage));

                if (usage >= 80.0) {
                    log.warn("[Solaris][경고] {} ({}) 사용률 {}% — 임계치(80%) 초과",
                            filesystem, mountPoint, String.format("%.2f", usage));
                }

                if (usage > highest.usagePercentage()) {
                    long totalKb = Long.parseLong(parts[1]);
                    long usedKb  = Long.parseLong(parts[2]);
                    long freeKb  = Long.parseLong(parts[3]);
                    highest = new DiskRawInfo(osName,
                            totalKb * 1024L, usedKb * 1024L, freeKb * 1024L, usage);
                }

            } catch (NumberFormatException e) {
                log.warn("[Solaris] df 파싱 실패 라인: {}", line);
            }
        }

        return highest;
    }

    /**
     * Solaris 가상/임시 파일시스템 여부 판단.
     * swap, proc, fd 등 실제 디스크가 아닌 항목을 집계에서 제외한다.
     */
    private static boolean isSolarisVirtualFs(String filesystem) {
        String lower = filesystem.toLowerCase();
        return lower.equals("swap")
                || lower.equals("proc")
                || lower.equals("fd")
                || lower.equals("mnttab")
                || lower.equals("ctfs")
                || lower.equals("objfs")
                || lower.equals("sharefs")
                || lower.startsWith("/proc")
                || lower.startsWith("/system/");
    }

    // ----------------------------------------------------------------
    // 공통 유틸
    // ----------------------------------------------------------------

    public static String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024 * 1024 * 1024) return String.format("%.1f TB", bytes / (1024.0 * 1024 * 1024 * 1024));
        if (bytes >= 1024L * 1024 * 1024)         return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        if (bytes >= 1024L * 1024)                 return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%d KB", bytes / 1024);
    }
}

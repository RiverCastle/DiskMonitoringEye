package com.monitoring.disk.service;

import com.monitoring.disk.dto.*;
import com.monitoring.disk.util.DiskMonitorUtil;
import com.monitoring.disk.util.OSCommandExecutor;
import com.monitoring.disk.util.OSCommandExecutor.OsType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class SystemResourceService {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // ----------------------------------------------------------------
    // 퍼블릭 API
    // ----------------------------------------------------------------

    public SystemStatusDto getSystemStatus() {
        OsType osType = OSCommandExecutor.detectOs();
        String osName = System.getProperty("os.name");

        double cpuUsage         = safeCollect("CPU",      () -> collectCpu(osType),     0.0);
        MemoryInfo memory       = safeCollect("메모리",    () -> collectMemory(osType),  emptyMemory());
        DiskInfo disk           = safeCollect("디스크",   this::collectDisk,            emptyDisk());
        int activeConnections   = safeCollect("네트워크", () -> collectNetwork(osType),  0);
        String uptime           = safeCollect("업타임",   () -> collectUptime(osType),  "알 수 없음");

        return new SystemStatusDto(
                osName,
                new CpuInfo(round1(cpuUsage), "%"),
                memory,
                disk,
                new NetworkInfo(activeConnections),
                uptime,
                LocalDateTime.now().format(FORMATTER)
        );
    }

    // ----------------------------------------------------------------
    // CPU
    // ----------------------------------------------------------------

    private double collectCpu(OsType os) {
        return switch (os) {
            case WINDOWS -> getWindowsCpu();
            case LINUX   -> getLinuxCpu();
            case SOLARIS -> getSolarisCpu();
            default      -> 0.0;
        };
    }

    /** PowerShell: 멀티 소켓 대응하여 평균값 반환 */
    private double getWindowsCpu() {
        String out = OSCommandExecutor.executePowerShell(
                "(Get-CimInstance Win32_Processor | Measure-Object -Property LoadPercentage -Average).Average"
        );
        return safeParseDouble(out.trim());
    }

    /** top -bn1 에서 idle(%)을 추출하여 100 - idle 반환 */
    private double getLinuxCpu() {
        String out = OSCommandExecutor.execute("top", "-bn1");
        Matcher m = Pattern.compile("(\\d+\\.\\d+)\\s+id").matcher(out);
        if (m.find()) {
            return round1(100.0 - Double.parseDouble(m.group(1)));
        }
        return 0.0;
    }

    /** vmstat 1 2 마지막 데이터 행의 us+sy 합산 */
    private double getSolarisCpu() {
        String out = OSCommandExecutor.execute("vmstat", "1", "2");
        String[] lines = out.split("\n");
        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();
            if (line.matches("^\\d+.*")) {
                String[] parts = line.split("\\s+");
                if (parts.length >= 3) {
                    // 마지막 컬럼이 id(idle)
                    int idle = (int) safeParseDouble(parts[parts.length - 1]);
                    return 100.0 - idle;
                }
            }
        }
        return 0.0;
    }

    // ----------------------------------------------------------------
    // 메모리
    // ----------------------------------------------------------------

    private MemoryInfo collectMemory(OsType os) {
        return switch (os) {
            case WINDOWS -> getWindowsMemory();
            case LINUX   -> getLinuxMemory();
            case SOLARIS -> getSolarisMemory();
            default      -> emptyMemory();
        };
    }

    /**
     * TotalVisibleMemorySize / FreePhysicalMemory (단위: KB)
     * 쌍따옴표 없이 각 값을 별도 줄로 출력 → ProcessBuilder 전달 시 파싱 오류 방지
     */
    private MemoryInfo getWindowsMemory() {
        String out = OSCommandExecutor.executePowerShell(
                "$os = Get-CimInstance Win32_OperatingSystem; " +
                "$os.TotalVisibleMemorySize; $os.FreePhysicalMemory"
        );
        String[] lines = out.trim().split("[\\r\\n]+");
        if (lines.length >= 2) {
            long totalKb = safeParseLong(lines[0].trim());
            long freeKb  = safeParseLong(lines[1].trim());
            if (totalKb > 0) {
                long totalMb = totalKb / 1024;
                long freeMb  = freeKb  / 1024;
                long usedMb  = totalMb - freeMb;
                return new MemoryInfo(totalMb, usedMb, freeMb, "MB",
                        round1(((double) usedMb / totalMb) * 100.0));
            }
        }
        return emptyMemory();
    }

    /** free -m: Mem 행에서 total / used / free 파싱 */
    private MemoryInfo getLinuxMemory() {
        String out = OSCommandExecutor.execute("free", "-m");
        Matcher m = Pattern.compile("Mem:\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)").matcher(out);
        if (m.find()) {
            long total = Long.parseLong(m.group(1));
            long used  = Long.parseLong(m.group(2));
            long free  = Long.parseLong(m.group(3));
            return new MemoryInfo(total, used, free, "MB",
                    round1(((double) used / total) * 100.0));
        }
        return emptyMemory();
    }

    /** prtconf으로 전체 용량, vmstat으로 여유 메모리(KB) 파싱 */
    private MemoryInfo getSolarisMemory() {
        String prtconf = OSCommandExecutor.execute("prtconf");
        Matcher m = Pattern.compile("Memory size:\\s+(\\d+)\\s+Megabytes").matcher(prtconf);
        if (m.find()) {
            long totalMb = Long.parseLong(m.group(1));
            String vmstat = OSCommandExecutor.execute("vmstat", "1", "1");
            for (String line : vmstat.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.matches("^\\d+.*")) {
                    String[] parts = trimmed.split("\\s+");
                    if (parts.length >= 5) {
                        long freeKb = safeParseLong(parts[4]);
                        long freeMb = freeKb / 1024;
                        long usedMb = totalMb - freeMb;
                        return new MemoryInfo(totalMb, usedMb, freeMb, "MB",
                                round1(((double) usedMb / totalMb) * 100.0));
                    }
                    break;
                }
            }
            return new MemoryInfo(totalMb, 0, totalMb, "MB", 0.0);
        }
        return emptyMemory();
    }

    // ----------------------------------------------------------------
    // 디스크 (기존 DiskMonitorUtil 재활용)
    // ----------------------------------------------------------------

    private DiskInfo collectDisk() {
        DiskRawInfo raw = DiskMonitorUtil.getDiskRawInfo();
        return new DiskInfo(
                round1(raw.usagePercentage()),
                formatBytes(raw.totalBytes()),
                formatBytes(raw.usedBytes()),
                formatBytes(raw.freeBytes())
        );
    }

    // ----------------------------------------------------------------
    // 네트워크
    // ----------------------------------------------------------------

    private int collectNetwork(OsType os) {
        return switch (os) {
            case WINDOWS -> getWindowsNetwork();
            case LINUX   -> getUnixNetwork();
            case SOLARIS -> getUnixNetwork();
            default      -> 0;
        };
    }

    /** ESTABLISHED 상태 TCP 연결 수 */
    private int getWindowsNetwork() {
        String out = OSCommandExecutor.executePowerShell(
                "(Get-NetTCPConnection -State Established -ErrorAction SilentlyContinue | Measure-Object).Count"
        );
        return (int) safeParseDouble(out.trim());
    }

    /** netstat -an 출력에서 ESTABLISHED 행 개수 집계 */
    private int getUnixNetwork() {
        String out = OSCommandExecutor.execute("netstat", "-an");
        return (int) Arrays.stream(out.split("\n"))
                .filter(line -> line.contains("ESTABLISHED"))
                .count();
    }

    // ----------------------------------------------------------------
    // 업타임
    // ----------------------------------------------------------------

    private String collectUptime(OsType os) {
        return switch (os) {
            case WINDOWS -> getWindowsUptime();
            case LINUX   -> getLinuxUptime();
            case SOLARIS -> getSolarisUptime();
            default      -> "알 수 없음";
        };
    }

    /**
     * 한글을 PowerShell 출력에 포함하면 CP949 → UTF-8 인코딩 불일치로 문자가 깨짐.
     * 숫자 3개만 출력하고 Java에서 포맷팅하여 인코딩 문제를 원천 차단.
     */
    private String getWindowsUptime() {
        String out = OSCommandExecutor.executePowerShell(
                "$u = (Get-Date) - (Get-CimInstance Win32_OperatingSystem).LastBootUpTime; " +
                "$u.Days.ToString() + ' ' + $u.Hours.ToString() + ' ' + $u.Minutes.ToString()"
        );
        String[] parts = out.trim().split("\\s+");
        if (parts.length >= 3) {
            return parts[0] + "일 " + parts[1] + "시간 " + parts[2] + "분";
        }
        return out.isBlank() ? "알 수 없음" : out.trim();
    }

    private String getLinuxUptime() {
        String out = OSCommandExecutor.execute("uptime", "-p").trim();
        return out.isBlank() ? "알 수 없음" : out;
    }

    private String getSolarisUptime() {
        String out = OSCommandExecutor.execute("uptime").trim();
        return out.isBlank() ? "알 수 없음" : out;
    }

    // ----------------------------------------------------------------
    // 공통 유틸
    // ----------------------------------------------------------------

    /** 각 수집 단계를 독립적으로 실행하여 한 항목 실패가 전체를 중단시키지 않도록 처리 */
    @SuppressWarnings("unchecked")
    private <T> T safeCollect(String label, CheckedSupplier<T> supplier, T defaultValue) {
        try {
            return supplier.get();
        } catch (Exception e) {
            log.error("[{}] 데이터 수집 실패: {}", label, e.getMessage());
            return defaultValue;
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private double safeParseDouble(String s) {
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0.0; }
    }

    private long safeParseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return 0L; }
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private String formatBytes(long bytes) {
        if (bytes >= 1024L * 1024 * 1024 * 1024) return String.format("%.1f TB", bytes / (1024.0 * 1024 * 1024 * 1024));
        if (bytes >= 1024L * 1024 * 1024)         return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
        if (bytes >= 1024L * 1024)                 return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%d KB", bytes / 1024);
    }

    private MemoryInfo emptyMemory() { return new MemoryInfo(0, 0, 0, "MB", 0.0); }
    private DiskInfo   emptyDisk()   { return new DiskInfo(0.0, "--", "--", "--"); }
}

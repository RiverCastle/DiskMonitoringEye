package com.monitoring.disk.service;

import com.monitoring.disk.dto.PortInfo;
import com.monitoring.disk.dto.PortKillResult;
import com.monitoring.disk.util.OSCommandExecutor;
import com.monitoring.disk.util.OSCommandExecutor.OsType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class PortManagementService {

    // ----------------------------------------------------------------
    // 퍼블릭 API
    // ----------------------------------------------------------------

    /** 현재 활성 포트 목록 조회 (LISTEN + ESTABLISHED, 프로세스명 포함) */
    public List<PortInfo> getActivePorts() {
        OsType os = OSCommandExecutor.detectOs();
        return switch (os) {
            case WINDOWS -> getWindowsPorts();
            case LINUX, SOLARIS -> getUnixPorts();
            default -> List.of();
        };
    }

    /**
     * 특정 포트를 점유 중인 프로세스를 강제 종료한다.
     * 해당 포트를 사용하는 첫 번째 PID를 대상으로 kill 한다.
     */
    public PortKillResult killPort(int port) {
        OsType os = OSCommandExecutor.detectOs();

        long pid = findPidByPort(port, os);
        if (pid <= 0) {
            return new PortKillResult(port, -1, false, "포트 " + port + "를 사용 중인 프로세스를 찾을 수 없습니다.");
        }

        return switch (os) {
            case WINDOWS -> killWindows(port, pid);
            case LINUX, SOLARIS -> killUnix(port, pid);
            default -> new PortKillResult(port, pid, false, "지원하지 않는 OS입니다.");
        };
    }

    // ----------------------------------------------------------------
    // Windows 구현
    // ----------------------------------------------------------------

    /**
     * netstat -ano 로 포트-PID 목록을 구하고,
     * tasklist 로 PID→프로세스명을 매핑하여 반환한다.
     */
    private List<PortInfo> getWindowsPorts() {
        String netstat = OSCommandExecutor.execute("netstat", "-ano");
        Map<Long, String> pidToName = buildPidNameMap();
        List<PortInfo> result = new ArrayList<>();

        for (String line : netstat.split("\n")) {
            line = line.trim();
            // TCP / UDP 행만 처리
            if (!line.startsWith("TCP") && !line.startsWith("UDP")) continue;

            String[] parts = line.split("\\s+");
            // TCP: Proto Local Foreign State PID (5컬럼)
            // UDP: Proto Local Foreign        PID (4컬럼 — State 없음)
            if (parts.length < 4) continue;

            String proto   = parts[0];
            String localAddr = parts[1];
            String remoteAddr = parts[2];
            String state;
            long   pid;

            if (proto.startsWith("TCP")) {
                if (parts.length < 5) continue;
                state = parts[3];
                pid   = parseLong(parts[4]);
            } else {
                state = "UDP";
                pid   = parseLong(parts[3]);
            }

            // LISTEN / ESTABLISHED 만 표시 (UDP 포함)
            if (!state.equals("LISTENING") && !state.equals("ESTABLISHED") && !state.equals("UDP")) continue;

            int port = parsePort(localAddr);
            if (port <= 0) continue;

            String processName = pidToName.getOrDefault(pid, "unknown");
            result.add(new PortInfo(proto, port, localAddr, remoteAddr, state, pid, processName));
        }
        return result;
    }

    /** tasklist /FO CSV 로 PID→프로세스명 맵 구성 */
    private Map<Long, String> buildPidNameMap() {
        Map<Long, String> map = new HashMap<>();
        String out = OSCommandExecutor.execute("tasklist", "/FO", "CSV", "/NH");
        for (String line : out.split("\n")) {
            line = line.trim().replace("\"", "");
            String[] cols = line.split(",");
            if (cols.length >= 2) {
                long pid = parseLong(cols[1].trim());
                if (pid > 0) map.put(pid, cols[0].trim());
            }
        }
        return map;
    }

    private PortKillResult killWindows(int port, long pid) {
        String out = OSCommandExecutor.execute("taskkill", "/PID", String.valueOf(pid), "/F");
        boolean success = out.contains("SUCCESS") || out.contains("성공");
        log.info("[PortKill][Windows] port={}, pid={}, result={}", port, pid, out.trim());
        return new PortKillResult(port, pid, success,
                success ? "PID " + pid + " 프로세스가 종료되었습니다." : "종료 실패: " + out.trim());
    }

    // ----------------------------------------------------------------
    // Linux / Solaris 구현
    // ----------------------------------------------------------------

    /**
     * ss -tlnp (Linux) 또는 netstat -tlnp (Solaris) 로 LISTEN 포트를 조회한다.
     * ESTABLISHED 연결은 netstat -tnp 로 보완한다.
     */
    private List<PortInfo> getUnixPorts() {
        List<PortInfo> result = new ArrayList<>();

        // LISTEN 포트 — ss 우선, 실패 시 netstat
        String ssOut = OSCommandExecutor.execute("ss", "-tlnp");
        if (ssOut.isBlank()) {
            ssOut = OSCommandExecutor.execute("netstat", "-tlnp");
        }
        parseSsOutput(ssOut, "LISTEN", result);

        // ESTABLISHED 연결
        String estOut = OSCommandExecutor.execute("ss", "-tnp");
        if (estOut.isBlank()) {
            estOut = OSCommandExecutor.execute("netstat", "-tnp");
        }
        parseSsOutput(estOut, "ESTABLISHED", result);

        return result;
    }

    /**
     * ss / netstat 출력 파싱.
     * ss 형식:  State  Recv-Q  Send-Q  Local  Peer  Process
     * netstat 형식: Proto Recv-Q Send-Q Local Foreign State PID/Name
     */
    private void parseSsOutput(String out, String targetState, List<PortInfo> result) {
        for (String line : out.split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("State") || line.startsWith("Proto")) continue;

            String[] parts = line.split("\\s+");
            if (parts.length < 5) continue;

            try {
                String state     = parts[0];
                String localAddr = parts[3];
                String peerAddr  = parts[4];
                int    port      = parsePort(localAddr);
                if (port <= 0) continue;

                // 프로세스 정보: "users:(("nginx",pid=1234,fd=6))"
                String procInfo = parts.length > 5 ? parts[5] : "";
                long   pid      = extractPidFromProcInfo(procInfo);
                String procName = extractNameFromProcInfo(procInfo);

                if (state.equalsIgnoreCase(targetState) || targetState.equals("LISTEN") && state.equalsIgnoreCase("LISTEN")) {
                    result.add(new PortInfo("TCP", port, localAddr, peerAddr, state.toUpperCase(), pid, procName));
                }
            } catch (Exception e) {
                log.debug("[PortList] 파싱 스킵: {}", line);
            }
        }
    }

    private long extractPidFromProcInfo(String procInfo) {
        // users:(("java",pid=12345,fd=10)) → 12345
        int pidIdx = procInfo.indexOf("pid=");
        if (pidIdx < 0) return -1;
        String sub = procInfo.substring(pidIdx + 4);
        return parseLong(sub.split("[,)]")[0]);
    }

    private String extractNameFromProcInfo(String procInfo) {
        // users:(("java",pid=12345,fd=10)) → java
        int start = procInfo.indexOf("((\"");
        if (start < 0) return "unknown";
        int end = procInfo.indexOf("\"", start + 3);
        return end > start ? procInfo.substring(start + 3, end) : "unknown";
    }

    private PortKillResult killUnix(int port, long pid) {
        String out = OSCommandExecutor.execute("kill", "-9", String.valueOf(pid));
        boolean success = out.isBlank(); // kill 성공 시 출력 없음
        log.info("[PortKill][Unix] port={}, pid={}, result={}", port, pid, out.trim());
        return new PortKillResult(port, pid, success,
                success ? "PID " + pid + " 프로세스가 종료되었습니다." : "종료 실패: " + out.trim());
    }

    // ----------------------------------------------------------------
    // 공통 유틸
    // ----------------------------------------------------------------

    /** 현재 OS 기준으로 포트에 해당하는 PID를 찾는다. */
    private long findPidByPort(int port, OsType os) {
        List<PortInfo> ports = getActivePorts();
        return ports.stream()
                .filter(p -> p.localPort() == port)
                .map(PortInfo::pid)
                .findFirst()
                .orElse(-1L);
    }

    /** "0.0.0.0:8085" 또는"[::]:8085" 형태에서 포트 번호를 추출한다. */
    private int parsePort(String addr) {
        if (addr == null || addr.isBlank()) return -1;
        int colonIdx = addr.lastIndexOf(':');
        if (colonIdx < 0) return -1;
        return (int) parseLong(addr.substring(colonIdx + 1));
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return -1; }
    }
}

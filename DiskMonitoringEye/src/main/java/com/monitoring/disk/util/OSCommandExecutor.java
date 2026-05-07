package com.monitoring.disk.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class OSCommandExecutor {

    private static final int TIMEOUT_SECONDS = 10;

    public enum OsType { WINDOWS, LINUX, SOLARIS, UNKNOWN }

    private OSCommandExecutor() {}

    public static OsType detectOs() {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("win"))                                                      return OsType.WINDOWS;
        if (osName.contains("nix") || osName.contains("nux") || osName.contains("mac")) return OsType.LINUX;
        if (osName.contains("sunos") || osName.contains("solaris"))                      return OsType.SOLARIS;
        return OsType.UNKNOWN;
    }

    /**
     * 주어진 명령어를 실행하고 표준 출력을 문자열로 반환합니다.
     * 오류 발생 또는 타임아웃 시 빈 문자열을 반환하며, 예외를 외부로 전파하지 않습니다.
     */
    public static String execute(String... command) {
        log.debug("명령어 실행: {}", Arrays.toString(command));
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            String output;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                output = reader.lines().collect(Collectors.joining("\n"));
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                log.warn("명령어 타임아웃 ({}초 초과): {}", TIMEOUT_SECONDS, Arrays.toString(command));
            }
            process.destroy();
            return output;

        } catch (Exception e) {
            log.error("명령어 실행 실패 [{}]: {}", Arrays.toString(command), e.getMessage());
            return "";
        }
    }

    /** Windows PowerShell 전용 실행 메서드 */
    public static String executePowerShell(String script) {
        return execute("powershell", "-NoProfile", "-NonInteractive", "-Command", script);
    }
}

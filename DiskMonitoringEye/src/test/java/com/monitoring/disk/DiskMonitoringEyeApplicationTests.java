package com.monitoring.disk;

import com.monitoring.disk.util.DiskMonitorUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DiskMonitoringEyeApplicationTests {

    @Test
    @DisplayName("애플리케이션 컨텍스트가 정상 로드되어야 한다")
    void contextLoads() {
    }

    @Test
    @DisplayName("현재 OS에서 디스크 사용률이 0% 초과 100% 이하로 반환되어야 한다")
    void diskUsageShouldBeValidRange() {
        double usage = DiskMonitorUtil.getDiskUsagePercentage();
        assertThat(usage).isGreaterThan(0.0);
        assertThat(usage).isLessThanOrEqualTo(100.0);
    }
}

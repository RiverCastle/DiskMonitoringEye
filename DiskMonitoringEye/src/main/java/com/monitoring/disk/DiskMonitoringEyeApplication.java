package com.monitoring.disk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DiskMonitoringEyeApplication {

    public static void main(String[] args) {
        SpringApplication.run(DiskMonitoringEyeApplication.class, args);
    }
}

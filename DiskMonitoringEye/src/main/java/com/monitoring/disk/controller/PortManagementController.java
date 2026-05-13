package com.monitoring.disk.controller;

import com.monitoring.disk.dto.PortInfo;
import com.monitoring.disk.dto.PortKillResult;
import com.monitoring.disk.service.PortManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/port")
@RequiredArgsConstructor
public class PortManagementController {

    private final PortManagementService portManagementService;

    /** 현재 사용 중인 포트 목록 조회 (LISTEN + ESTABLISHED, 프로세스명 포함) */
    @GetMapping("/list")
    public ResponseEntity<List<PortInfo>> getActivePorts() {
        return ResponseEntity.ok(portManagementService.getActivePorts());
    }

    /** 특정 포트를 점유 중인 프로세스 강제 종료 */
    @DeleteMapping("/{port}")
    public ResponseEntity<PortKillResult> killPort(@PathVariable int port) {
        PortKillResult result = portManagementService.killPort(port);
        return result.success()
                ? ResponseEntity.ok(result)
                : ResponseEntity.internalServerError().body(result);
    }
}

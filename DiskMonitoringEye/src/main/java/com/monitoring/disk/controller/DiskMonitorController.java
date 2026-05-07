package com.monitoring.disk.controller;

import com.monitoring.disk.dto.DiskStatusDto;
import com.monitoring.disk.dto.DriveInfo;
import com.monitoring.disk.dto.SystemStatusDto;
import com.monitoring.disk.service.DiskMonitorService;
import com.monitoring.disk.service.SystemResourceService;
import com.monitoring.disk.util.DiskMonitorUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class DiskMonitorController {

    private final DiskMonitorService diskMonitorService;
    private final SystemResourceService systemResourceService;

    /** 기존 디스크 전용 API (하위 호환 유지) */
    @GetMapping("/disk")
    public ResponseEntity<DiskStatusDto> getDiskStatus() {
        return ResponseEntity.ok(diskMonitorService.getCurrentStatus());
    }

    /** 종합 서버 리소스 API (CPU · 메모리 · 디스크 · 네트워크 · 업타임) */
    @GetMapping("/system-status")
    public ResponseEntity<SystemStatusDto> getSystemStatus() {
        return ResponseEntity.ok(systemResourceService.getSystemStatus());
    }

    /**
     * 드라이브 목록 API
     * Windows: C:\, D:\ 등 마운트된 모든 로컬 드라이브
     * Linux/Mac: 루트(/) 단일 항목
     * 총 용량이 0인 드라이브(빈 CD-ROM 등)는 제외
     */
    @GetMapping("/disk/drives")
    public ResponseEntity<List<DriveInfo>> getDriveList() {
        return ResponseEntity.ok(DiskMonitorUtil.getAllDrives());
    }
}

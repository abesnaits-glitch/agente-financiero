package com.agentefinanciero.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Paths;

@RestController
public class ReporteController {

    @Value("${app.reports-dir:/tmp/faro-reports}")
    private String reportsDirPath;

    @GetMapping("/reports/{filename:.+}")
    public ResponseEntity<Resource> serveReport(@PathVariable String filename) {
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\"))
            return ResponseEntity.badRequest().build();
        Resource resource = new FileSystemResource(Paths.get(reportsDirPath).resolve(filename));
        if (!resource.exists() || !resource.isReadable())
            return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
                .body(resource);
    }
}

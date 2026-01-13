package ch.so.agi.ask.api;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ch.so.agi.ask.mcp.PrintFileStorage;

@RestController
@RequestMapping("${landreg.print.download-base-path:/api/prints}")
public class PrintDownloadController {
    private static final Logger log = LoggerFactory.getLogger(PrintDownloadController.class);
    private final PrintFileStorage storage;

    public PrintDownloadController(PrintFileStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable(name = "id") String id) {
        PrintFileStorage.ResourceWithMeta meta = storage.retrieve(id);
        if (meta == null) {
            return ResponseEntity.notFound().build();
        }
        Resource res = meta.resource();
        String filename = Optional.ofNullable(meta.path().getFileName()).map(Object::toString).orElse(id + ".pdf");
        long length = contentLengthSafe(meta);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentLength(length >= 0 ? length : -1)
                .body(res);
    }

    private long contentLengthSafe(PrintFileStorage.ResourceWithMeta meta) {
        try {
            return Files.size(meta.path());
        } catch (IOException e) {
            log.warn("Cannot determine size for {}", meta.path(), e);
            return -1;
        }
    }
}

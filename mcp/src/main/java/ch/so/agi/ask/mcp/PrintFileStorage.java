package ch.so.agi.ask.mcp;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import ch.so.agi.ask.config.LandregPrintProperties;

@Component
public class PrintFileStorage {
    private static final Logger log = LoggerFactory.getLogger(PrintFileStorage.class);

    private final LandregPrintProperties properties;
    private final Clock clock;
    private final Map<String, StoredFile> files = new ConcurrentHashMap<>();

    public PrintFileStorage(LandregPrintProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public record StoredPdf(String id, String url, Instant expiresAt, long size) {
    }

    private record StoredFile(Path path, Instant expiresAt) {
    }

    public StoredPdf storePdf(byte[] data) throws IOException {
        cleanupExpired();

        String id = UUID.randomUUID().toString();
        Path baseDir = resolveBaseDir();
        Files.createDirectories(baseDir);
        Path target = baseDir.resolve(id + ".pdf");
        Files.write(target, data);

        Instant expiresAt = Instant.now(clock).plusSeconds(Math.max(1, properties.getStorageTtlSeconds()));
        files.put(id, new StoredFile(target, expiresAt));

        String url = normalizeBasePath(properties.getDownloadBasePath()) + "/" + id;
        return new StoredPdf(id, url, expiresAt, data.length);
    }

    public ResourceWithMeta retrieve(String id) {
        cleanupExpired();
        StoredFile stored = files.get(id);
        if (stored == null || stored.expiresAt().isBefore(Instant.now(clock))) {
            removeQuietly(id, stored);
            return null;
        }
        if (!Files.exists(stored.path())) {
            removeQuietly(id, stored);
            return null;
        }
        return new ResourceWithMeta(new FileSystemResource(stored.path()), stored.path());
    }

    public void cleanupExpired() {
        Instant now = Instant.now(clock);
        files.entrySet().removeIf(entry -> {
            StoredFile stored = entry.getValue();
            if (stored == null || stored.expiresAt().isBefore(now) || !Files.exists(stored.path())) {
                removeQuietly(entry.getKey(), stored);
                return true;
            }
            return false;
        });
    }

    private void removeQuietly(String id, StoredFile stored) {
        try {
            if (stored != null && stored.path() != null) {
                Files.deleteIfExists(stored.path());
            }
        } catch (IOException e) {
            log.warn("Failed to delete temp print file {}", stored != null ? stored.path() : id, e);
        }
        files.remove(id);
    }

    private Path resolveBaseDir() {
        if (StringUtils.hasText(properties.getStorageDirectory())) {
            return Paths.get(properties.getStorageDirectory());
        }
        return Paths.get(System.getProperty("java.io.tmpdir"), "ask-sogis-prints");
    }

    private String normalizeBasePath(String basePath) {
        if (!StringUtils.hasText(basePath)) {
            return "/api/prints";
        }
        if (basePath.endsWith("/")) {
            return basePath.substring(0, basePath.length() - 1);
        }
        return basePath;
    }

    public record ResourceWithMeta(Resource resource, Path path) {
    }
}

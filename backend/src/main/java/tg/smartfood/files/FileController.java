package tg.smartfood.files;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileStorage storage;

    public FileController(FileStorage storage) {
        this.storage = storage;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('RESTAURANT', 'ADMIN', 'SUPERADMIN')")
    public Map<String, String> upload(@RequestParam("folder") String folder, @RequestParam("file") MultipartFile file) {
        String key = storage.store(folder, file);
        return Map.of("key", key, "url", "/api/v1/files/" + key);
    }

    @GetMapping("/{folder}/{name}")
    public ResponseEntity<Resource> download(@PathVariable String folder, @PathVariable String name) {
        Resource resource = storage.load(folder + "/" + name);
        MediaType type = name.endsWith(".png") ? MediaType.IMAGE_PNG
                : name.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .body(resource);
    }
}

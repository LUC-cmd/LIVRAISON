package tg.smartfood.files;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tg.smartfood.common.ApiException;
import tg.smartfood.common.AppProps;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Stockage sur disque local, hors de PostgreSQL. Remplacable par un stockage objet (S3, MinIO) via la meme interface. */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Set<String> FOLDERS = Set.of("logos", "dishes", "profiles");
    private static final Map<String, String> EXTENSIONS = Map.of(
            "image/jpeg", ".jpg", "image/png", ".png", "image/webp", ".webp");

    private final Path root;

    public LocalFileStorage(AppProps props) {
        this.root = Paths.get(props.getStorageDir()).toAbsolutePath().normalize();
    }

    @Override
    public String store(String folder, MultipartFile file) {
        if (!FOLDERS.contains(folder)) {
            throw ApiException.badRequest("BAD_FOLDER", "Dossier de stockage inconnu.");
        }
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Le fichier est vide.");
        }
        String extension = EXTENSIONS.get(file.getContentType());
        if (extension == null) {
            throw ApiException.badRequest("BAD_FILE_TYPE", "Seules les images JPEG, PNG et WebP sont acceptees.");
        }
        String name = UUID.randomUUID().toString().replace("-", "") + extension;
        try {
            Path directory = root.resolve(folder);
            Files.createDirectories(directory);
            file.transferTo(directory.resolve(name));
        } catch (IOException e) {
            throw new UncheckedIOException("Enregistrement du fichier impossible", e);
        }
        return folder + "/" + name;
    }

    @Override
    public Resource load(String key) {
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || !Files.isRegularFile(path)) {
            throw ApiException.notFound("Fichier introuvable.");
        }
        return new FileSystemResource(path);
    }
}

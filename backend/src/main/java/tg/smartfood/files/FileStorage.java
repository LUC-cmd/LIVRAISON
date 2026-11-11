package tg.smartfood.files;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/** Stockage externe des images (logos, plats, profils). La base ne garde que la cle. */
public interface FileStorage {

    /** Enregistre le fichier et retourne sa cle de stockage, par exemple "dishes/3f2a...c1.jpg". */
    String store(String folder, MultipartFile file);

    Resource load(String key);
}

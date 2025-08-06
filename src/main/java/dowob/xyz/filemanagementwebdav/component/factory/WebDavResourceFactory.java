package dowob.xyz.filemanagementwebdav.component.factory;

import dowob.xyz.filemanagementwebdav.data.FileMetadata;
import dowob.xyz.filemanagementwebdav.data.WebDavFileResource;
import dowob.xyz.filemanagementwebdav.data.WebDavFolderResource;
import dowob.xyz.filemanagementwebdav.service.FileProcessingService;
import io.milton.common.Path;
import io.milton.http.ResourceFactory;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavResourceFactory
 * @create 2025/6/9
 * @Version 1.0
 **/
@Service
@RequiredArgsConstructor
public class WebDavResourceFactory implements ResourceFactory {
    private final FileProcessingService fileProcessingService;
    private final Map<String, FileMetadata> metadataCache = new ConcurrentHashMap<>();

    @Override
    public Resource getResource(String host, String requestPath) throws NotAuthorizedException, BadRequestException {
        Path path = Path.path(requestPath);

        if (path.isRoot()) {
            FileMetadata rootMetadata = FileMetadata.builder()
                    .name("/")
                    .path("/")
                    .isDirectory(true)
                    .size(0)
                    .contentType("")
                    .createDate(java.time.LocalDateTime.now())
                    .modifiedDate(java.time.LocalDateTime.now())
                    .build();
            return new WebDavFolderResource(host, path, rootMetadata, this, fileProcessingService);
        }

        FileMetadata metadata = metadataCache.get(requestPath);
        if (metadata == null) {
            metadata = fileProcessingService.getFileMetadata(path);
            if (metadata != null) {
                metadataCache.put(requestPath, metadata);
            }
        }

        if (metadata != null) {
            if (metadata.isDirectory()) {
                return new WebDavFolderResource(host, path, metadata, this, fileProcessingService);
            }
            return new WebDavFileResource(host, path, metadata, fileProcessingService);
        }

        return null;
    }

    public void invalidateCache(String requestPath) {
        metadataCache.remove(requestPath);
    }
}

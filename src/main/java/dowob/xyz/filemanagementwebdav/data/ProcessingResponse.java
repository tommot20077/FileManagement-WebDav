package dowob.xyz.filemanagementwebdav.data;

import lombok.Builder;
import lombok.Data;

import java.io.InputStream;
import java.util.List;

/**
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName ProcessingResponse
 * @create 2025/6/9
 * @Version 1.0
 **/

@Data
@Builder
public class ProcessingResponse {
    private boolean success;
    private String errorMessage;
    private InputStream dataStream;
    private List<FileMetadata> children; // For LIST operation
    private FileMetadata metadata;
}
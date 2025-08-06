package dowob.xyz.filemanagementwebdav.data;

import dowob.xyz.filemanagementwebdav.customerenum.Operation;
import dowob.xyz.filemanagementwebdav.data.dto.AuthInfoDto;
import dowob.xyz.filemanagementwebdav.service.FileProcessingService;
import io.milton.common.Path;
import io.milton.http.Auth;
import io.milton.http.FileItem;
import io.milton.http.Range;
import io.milton.http.Request;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.*;

import java.io.IOException;
import java.io.OutputStream;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

/**
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavFileResource
 * @create 2025/6/9
 * @Version 1.0
 **/

public class WebDavFileResource implements FileResource, PropFindableResource, DeletableResource, MoveableResource, CopyableResource {
    private final String host;

    private final Path path;

    private final FileMetadata metadata;

    private final FileProcessingService fileProcessingService;


    public WebDavFileResource(String host, Path path, FileMetadata metadata, FileProcessingService fileProcessingService) {
        this.host = host;
        this.path = path;
        this.metadata = metadata;
        this.fileProcessingService = fileProcessingService;
    }


    @Override
    public String getUniqueId() {
        return metadata.getId() != null ? metadata.getId().toString() : null;
    }


    @Override
    public String getName() {
        return path.getName();
    }


    @Override
    public Object authenticate(String username, String password) {
        return new AuthInfoDto(username, password);
    }


    @Override
    public boolean authorise(Request request, Request.Method method, Auth auth) {
        return true;
    }


    @Override
    public String getRealm() {
        return "WebDAV";
    }


    @Override
    public Date getModifiedDate() {
        if (metadata.getModifiedDate() == null) {
            return null;
        }

        return Date.from(metadata.getModifiedDate().atZone(ZoneId.systemDefault()).toInstant());
    }


    @Override
    public String checkRedirect(Request request) {
        return null;
    }


    @Override
    public Long getContentLength() {
        return metadata.getSize();
    }


    @Override
    public String getContentType(String accepts) {
        return metadata.getContentType();
    }


    @Override
    public void sendContent(OutputStream out, Range range, Map<String, String> params, String contentType) throws IOException {
        ProcessingRequest request = ProcessingRequest.builder().path(path.toString()).operation(Operation.READ).range(range).build();
        ProcessingResponse response = fileProcessingService.processFile(request);

        if (!response.isSuccess()) {
            throw new IOException("無法讀取檔案: " + response.getErrorMessage());
        }
        response.getDataStream().transferTo(out);
    }


    @Override
    public Long getMaxAgeSeconds(Auth auth) {
        return 0L;
    }


    @Override
    public void delete() throws NotAuthorizedException, ConflictException, BadRequestException {
        ProcessingRequest request = ProcessingRequest.builder().path(path.toString()).operation(Operation.DELETE).build();

        ProcessingResponse response = fileProcessingService.processFile(request);

        if (!response.isSuccess()) {
            throw new BadRequestException("無法刪除檔案: " + response.getErrorMessage());
        }
    }


    @Override
    public void moveTo(CollectionResource newParent, String newName) throws ConflictException, NotAuthorizedException, BadRequestException {
        String newPath = ((WebDavFolderResource) newParent).getPath().child(newName).toString();

        ProcessingRequest request = ProcessingRequest.builder().path(path.toString()).operation(Operation.MOVE).targetPath(newPath).build();

        ProcessingResponse response = fileProcessingService.processFile(request);

        if (!response.isSuccess()) {
            throw new BadRequestException("無法移動檔案: " + response.getErrorMessage());
        }
    }


    @Override
    public void copyTo(CollectionResource newParent, String newName) throws NotAuthorizedException, BadRequestException, ConflictException {
        String newPath = ((WebDavFolderResource) newParent).getPath().child(newName).toString();

        ProcessingRequest request = ProcessingRequest.builder().path(path.toString()).operation(Operation.COPY).targetPath(newPath).build();

        ProcessingResponse response = fileProcessingService.processFile(request);

        if (!response.isSuccess()) {
            throw new BadRequestException("無法複製檔案: " + response.getErrorMessage());
        }
    }


    @Override
    public Date getCreateDate() {
        if (metadata.getCreateDate() == null) {
            return null;
        }
        return Date.from(metadata.getCreateDate().atZone(ZoneId.systemDefault()).toInstant());
    }


    @Override
    public String processForm(Map<String, String> map, Map<String, FileItem> map1) {
        throw new UnsupportedOperationException("不支持透過表單上傳, 請使用 WebDAV 客戶端或 API 進行文件上傳");
    }
}

package dowob.xyz.filemanagementwebdav.data;

import dowob.xyz.filemanagementwebdav.component.factory.WebDavResourceFactory;
import dowob.xyz.filemanagementwebdav.customerenum.Operation;
import dowob.xyz.filemanagementwebdav.data.dto.AuthInfoDto;
import dowob.xyz.filemanagementwebdav.service.FileProcessingService;
import io.milton.common.Path;
import io.milton.http.Auth;
import io.milton.http.Request;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.resource.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName WebDavFolderResource
 * @create 2025/6/9
 * @Version 1.0
 **/

public class WebDavFolderResource implements CollectionResource, PropFindableResource, MakeCollectionableResource, PutableResource, DeletableResource {
    private final String host;

    private final Path path;

    private FileMetadata metadata;

    private final WebDavResourceFactory resourceFactory;

    private final FileProcessingService fileProcessingService;


    public WebDavFolderResource(String host, Path path, FileMetadata metadata, WebDavResourceFactory resourceFactory, FileProcessingService fileProcessingService) {
        this.host = host;
        this.path = path;
        this.metadata = metadata;
        this.resourceFactory = resourceFactory;
        this.fileProcessingService = fileProcessingService;
    }


    @Override
    public String getUniqueId() {
        return path.toString();
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
    public Resource child(String childName) throws NotAuthorizedException, BadRequestException {
        Path childPath = path.child(childName);
        return resourceFactory.getResource(host, childPath.toString());
    }


    @Override
    public List<? extends Resource> getChildren() throws NotAuthorizedException, BadRequestException {
        ProcessingRequest request = ProcessingRequest.builder().path(path.toString()).operation(Operation.LIST).build();

        ProcessingResponse response = fileProcessingService.processFile(request);

        if (response.isSuccess() && response.getChildren() != null) {
            return response.getChildren().stream().map(child -> {
                try {
                    return resourceFactory.getResource(host, path.child(child.getName()).toString());
                } catch (NotAuthorizedException | BadRequestException e) {
                    System.err.println("無法獲取子資源: " + child.getName() + ", 錯誤: " + e.getMessage());
                    return null;
                }
            }).filter(Objects::nonNull).collect(Collectors.toList());
        }

        return List.of();
    }


    @Override
    public CollectionResource createCollection(String newName) throws NotAuthorizedException, ConflictException, BadRequestException {
        String newPath = path.child(newName).toString();

        ProcessingRequest request = ProcessingRequest.builder().path(newPath).operation(Operation.MKCOL).build();

        ProcessingResponse response = fileProcessingService.processFile(request);

        if (!response.isSuccess()) {
            throw new BadRequestException("無法創建集合: " + response.getErrorMessage());
        }

        resourceFactory.invalidateCache(newPath);
        FileMetadata newFolderMetadata = FileMetadata.builder()
                .name(newName)
                .path(newPath)
                .isDirectory(true)
                .size(0)
                .contentType("")
                .createDate(java.time.LocalDateTime.now())
                .modifiedDate(java.time.LocalDateTime.now())
                .build();
        return new WebDavFolderResource(host, Path.path(newPath), newFolderMetadata, resourceFactory, fileProcessingService);
    }


    @Override
    public Resource createNew(String newName, InputStream inputStream, Long length, String contentType) throws IOException, ConflictException, NotAuthorizedException, BadRequestException {
        String newPath = path.child(newName).toString();

        ProcessingRequest request = ProcessingRequest
                .builder()
                .path(newPath)
                .operation(Operation.PUT)
                .contentType(contentType)
                .contentLength(length)
                .dataStream(inputStream)
                .build();

        ProcessingResponse response = fileProcessingService.processFile(request);

        if (!response.isSuccess()) {
            throw new BadRequestException("Failed to create file: " + response.getErrorMessage());
        }

        resourceFactory.invalidateCache(newPath);
        return resourceFactory.getResource(host, newPath);
    }


    @Override
    public void delete() throws NotAuthorizedException, ConflictException, BadRequestException {
        ProcessingRequest request = ProcessingRequest.builder().path(path.toString()).operation(Operation.DELETE).build();

        ProcessingResponse response = fileProcessingService.processFile(request);

        if (!response.isSuccess()) {
            throw new BadRequestException("Failed to delete folder: " + response.getErrorMessage());
        }
    }


    @Override
    public Date getCreateDate() {
        if (metadata.getCreateDate() == null) {
            return null;
        }
        return Date.from(metadata.getCreateDate().atZone(ZoneId.systemDefault()).toInstant());
    }


    public Path getPath() {
        return path;
    }
}

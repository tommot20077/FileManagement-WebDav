package dowob.xyz.filemanagementwebdav.data.dto;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * @author yuan
 * @program FileManagement-WebDAV
 * @ClassName AuthInfoDto
 * @create 2025/7/28
 * @Version 1.0
 **/
@Data
public class AuthInfoDto {
    private String username;
    private String password;
    private Map<String, Object> credentials;

    public AuthInfoDto(String username, String password) {
        this.username = username;
        this.password = password;
        this.credentials = new HashMap<>();
    }
}

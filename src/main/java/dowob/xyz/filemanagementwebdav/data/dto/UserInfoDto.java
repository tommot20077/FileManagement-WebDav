package dowob.xyz.filemanagementwebdav.data.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 用戶資訊資料傳輸物件，封裝從 JWT 令牌中提取的完整用戶身份資訊。
 * 
 * <p>此物件包含用戶的基本識別資訊（ID、用戶名稱）、權限資訊（角色）以及令牌有效期等關鍵資料。
 * 設計用於替代單純的 userId 傳遞，提供更完整的用戶上下文資訊，減少重複的令牌解析操作。
 * 
 * <p>主要應用場景包括：
 * <ul>
 *   <li>身份驗證快取儲存</li>
 *   <li>WebDAV 資源存取控制</li>
 *   <li>跨服務用戶資訊傳遞</li>
 *   <li>審計日誌記錄</li>
 * </ul>
 * 
 * @author yuan
 * @version 1.0
 * @since 1.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoDto {
    
    /**
     * 用戶唯一識別碼
     * <p>
     * 對應資料庫中的用戶主鍵，用於唯一標識系統中的用戶實體。
     */
    private Long userId;
    
    /**
     * 用戶名稱
     * <p>
     * 用戶的登入名稱或顯示名稱，通常用於日誌記錄和用戶介面顯示。
     */
    private String username;
    
    /**
     * 用戶角色
     * <p>
     * 用戶的系統角色，決定其存取權限範圍。例如：ADMIN、USER、GUEST 等。
     * 從主服務的 JWT 令牌中提取，為單一角色字串而非角色列表。
     */
    private String role;
    
    /**
     * 令牌過期時間
     * <p>
     * JWT 令牌的過期時間戳記，用於快取有效性檢查和自動清理機制。
     * 當此時間早於當前時間時，表示令牌已失效，需要重新驗證。
     */
    private Date tokenExpiry;
    
    /**
     * 令牌版本號
     * <p>
     * 用於令牌撤銷機制的版本控制，當用戶登出或密碼變更時版本號會更新。
     * 透過版本號比對可以快速判斷令牌是否已被撤銷。
     */
    private String tokenVersion;
    
    /**
     * 檢查用戶是否具有管理員權限
     * 
     * @return true 如果用戶角色為 ADMIN
     */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
    
    /**
     * 檢查令牌是否已過期
     * 
     * @return true 如果令牌已過期
     */
    public boolean isExpired() {
        return tokenExpiry != null && tokenExpiry.before(new Date());
    }
    
    /**
     * 檢查用戶是否具有指定角色
     * 
     * @param expectedRole 預期的角色名稱
     * @return true 如果用戶角色匹配
     */
    public boolean hasRole(String expectedRole) {
        return role != null && role.equals(expectedRole);
    }
}
import java.sql.*;

public class Unlock {
    public static void main(String[] a) throws Exception {
        String url = "jdbc:mysql://192.168.100.128:3306/industrial_agent?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        try (Connection c = DriverManager.getConnection(url, "root", "1234");
             Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT id, username, status, is_deleted FROM sys_user ORDER BY id")) {
                while (rs.next()) {
                    System.out.println(rs.getInt(1) + " | " + rs.getString(2) + " | status=" + rs.getInt(3) + " | del=" + rs.getInt(4));
                }
            }
            // 兜底：确保两个账号可用 + 角色未停用
            s.executeUpdate("UPDATE sys_user SET status = 1, is_deleted = 0 WHERE username IN ('admin','operator')");
            s.executeUpdate("UPDATE sys_role SET status = 1, is_deleted = 0 WHERE role_key IN ('ADMIN','MANAGER','OPERATOR')");
            System.out.println("normalized.");
        }
    }
}
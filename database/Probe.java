import java.sql.*;

public class Probe {
    public static void main(String[] a) throws Exception {
        String url = "jdbc:mysql://192.168.100.128:3306/industrial_agent?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        try (Connection c = DriverManager.getConnection(url, "root", "1234");
             Statement s = c.createStatement()) {
            try (ResultSet rs = s.executeQuery("SELECT id, username, real_name, role_id, status, is_deleted, password FROM sys_user ORDER BY id")) {
                while (rs.next()) {
                    String p = rs.getString("password");
                    System.out.println(rs.getInt(1) + " | " + rs.getString(2) + " | " + rs.getString(3)
                            + " | role=" + rs.getInt(4) + " | status=" + rs.getInt(5) + " | del=" + rs.getInt(6)
                            + " | pwd=" + (p == null ? "NULL" : p.substring(0, Math.min(20, p.length()))));
                }
            }
            System.out.println("--- sys_role ---");
            try (ResultSet rs = s.executeQuery("SELECT id, role_name, role_key, status, is_deleted FROM sys_role ORDER BY id")) {
                while (rs.next()) {
                    System.out.println(rs.getInt(1) + " | " + rs.getString(2) + " | " + rs.getString(3)
                            + " | status=" + rs.getInt(4) + " | del=" + rs.getInt(5));
                }
            }
        }
    }
}
import java.sql.*;

public class FixRole {
    public static void main(String[] a) throws Exception {
        String url = "jdbc:mysql://192.168.100.128:3306/industrial_agent?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
        try (Connection c = DriverManager.getConnection(url, "root", "1234")) {
            c.setAutoCommit(true);
            // 1) 打印当前状态（用 HEX 判断是否已是正确 UTF-8）
            String[][] roles = {
                {"1", "系统管理员", "ADMIN"},
                {"2", "车间主管", "MANAGER"},
                {"3", "操作员", "OPERATOR"}
            };
            for (String[] r : roles) {
                String utf8 = r[1];
                // 脏数据特征：原 UTF-8 字节被当作 GBK 解码后再以 UTF-8 存储
                String garbled = new String(utf8.getBytes("GBK"), "UTF-8");
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE sys_role SET role_name = ? WHERE id = ? AND (role_name = ? OR role_name = ?)")) {
                    ps.setString(1, utf8);
                    ps.setInt(2, Integer.parseInt(r[0]));
                    ps.setString(3, garbled);
                    ps.setString(4, utf8);
                    int n = ps.executeUpdate();
                    System.out.println("role " + r[0] + " -> " + utf8 + " updated=" + n);
                }
            }
            // 2) 直接按 id 强制修正（兜底，防止上面字符匹配不上）
            for (String[] r : roles) {
                try (PreparedStatement ps = c.prepareStatement("UPDATE sys_role SET role_name = ? WHERE id = ?")) {
                    ps.setString(1, r[1]);
                    ps.setInt(2, Integer.parseInt(r[0]));
                    ps.executeUpdate();
                }
            }
            System.out.println("--- after ---");
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT id, role_name, role_key, HEX(role_name) FROM sys_role ORDER BY id")) {
                while (rs.next()) {
                    System.out.println(rs.getInt(1) + " | " + rs.getString(2) + " | " + rs.getString(3) + " | " + rs.getString(4));
                }
            }
            System.out.println("--- sys_user ---");
            try (Statement s = c.createStatement();
                 ResultSet rs = s.executeQuery("SELECT id, username, real_name, role_id FROM sys_user ORDER BY id")) {
                while (rs.next()) {
                    System.out.println(rs.getInt(1) + " | " + rs.getString(2) + " | " + rs.getString(3) + " | " + rs.getInt(4));
                }
            }
        }
    }
}
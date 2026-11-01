import java.sql.*;

String URL = "jdbc:mysql://192.168.100.128:3306/industrial_agent?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true";
String USER = "root", PWD = "1234";

Connection c = DriverManager.getConnection(URL, USER, PWD);
c.setAutoCommit(false);

try (Statement st = c.createStatement()) {
    // 1) 角色
    st.executeUpdate("DELETE FROM sys_role WHERE role_key IN ('ADMIN','MANAGER','OPERATOR')");
    st.executeUpdate("INSERT INTO sys_role(role_name, role_key, status, create_time, update_time, is_deleted) VALUES " +
        "('系统管理员','ADMIN',1,NOW(),NOW(),0)," +
        "('车间主管','MANAGER',1,NOW(),NOW(),0)," +
        "('操作员','OPERATOR',1,NOW(),NOW(),0)");
    ResultSet rs = st.executeQuery("SELECT id, role_key FROM sys_role WHERE is_deleted=0");
    while (rs.next()) System.out.println("role: " + rs.getLong(1) + " " + rs.getString(2));
    rs.close();

    // 2) 用户
    long adminRole = 0, opRole = 0;
    ResultSet r2 = st.executeQuery("SELECT id, role_key FROM sys_role WHERE role_key IN ('ADMIN','OPERATOR')");
    while (r2.next()) {
        if ("ADMIN".equals(r2.getString(2))) adminRole = r2.getLong(1);
        else opRole = r2.getLong(1);
    }
    r2.close();

    st.executeUpdate("DELETE FROM sys_user WHERE username IN ('admin','operator')");
    st.executeUpdate("INSERT INTO sys_user(username,password,real_name,role_id,workshop_id,status,create_time,update_time,is_deleted) VALUES " +
        "('admin','$2a$10$LEdF2cG0cAOGRMRrDD4GsOefNwQExbcp9KDOr85caoSbkmHtIUqi.','吴运成'," + adminRole + ",1,1,NOW(),NOW(),0)," +
        "('operator','$2a$10$e0JQk.0AYAfvKRXiLti8NOO4ehCr0SUna27/gHDUNpLjJfx.HoMgu','赵德海'," + opRole + ",1,1,NOW(),NOW(),0)");
    ResultSet r3 = st.executeQuery("SELECT id, username, real_name, role_id, status FROM sys_user WHERE is_deleted=0");
    while (r3.next()) System.out.println("user: " + r3.getLong(1) + " " + r3.getString(2) + " " + r3.getString(3) + " role=" + r3.getLong(4) + " status=" + r3.getInt(5));
    r3.close();
}

c.commit();
c.close();
System.out.println("SEED OK");

/exit
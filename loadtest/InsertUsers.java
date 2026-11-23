import java.sql.*;
import java.time.LocalDateTime;

/** 批量创建压测用学生账号：java InsertUsers <count> <offset> <passwordHash> */
public class InsertUsers {
    public static void main(String[] args) throws Exception {
        int count = Integer.parseInt(args[0]);
        int offset = Integer.parseInt(args[1]);
        String hash = args[2];
        String url = "jdbc:mysql://192.168.150.101:3306/tj_user?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
        Class.forName("com.mysql.cj.jdbc.Driver");
        String sql = "insert into `user`(id, username, cell_phone, password, type, status, create_time, update_time, creater, updater) " +
                "values(?,?,?,?,2,1,now(),now(),1,1) on duplicate key update password=values(password), status=1";
        try (Connection c = DriverManager.getConnection(url, "root", "123");
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < count; i++) {
                int n = offset + i;
                String phone = String.format("139%08d", n);
                ps.setLong(1, 1700000000000000000L + n);
                ps.setString(2, "lt" + phone);
                ps.setString(3, phone);
                ps.setString(4, hash);
                ps.addBatch();
            }
            int[] r = ps.executeBatch();
            System.out.println("inserted/updated rows: " + r.length);
        }
        try (Connection c = DriverManager.getConnection(url, "root", "123");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select count(*) from `user` where id >= 1700000000000000000")) {
            rs.next();
            System.out.println("test users total: " + rs.getLong(1));
        }
    }
}
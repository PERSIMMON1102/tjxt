import java.sql.*;

public class DbQuery {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://192.168.150.101:3306/?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
        String user = "root", pass = "123";
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            String sql = args.length > 0 ? args[0] : "show databases";
            try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                ResultSetMetaData md = rs.getMetaData();
                int n = md.getColumnCount();
                while (rs.next()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i <= n; i++) {
                        sb.append(md.getColumnLabel(i)).append("=").append(rs.getString(i));
                        if (i < n) sb.append(" | ");
                    }
                    System.out.println(sb);
                }
            }
        }
    }
}
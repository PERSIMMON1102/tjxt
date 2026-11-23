import java.sql.*;

/** 执行任意 SQL：java SqlExec "<sql>" */
public class SqlExec {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://192.168.150.101:3306/tj_promotion?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false";
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection(url, "root", "123");
             Statement st = c.createStatement()) {
            boolean hasRs = st.execute(args[0]);
            if (hasRs) {
                try (ResultSet rs = st.getResultSet()) {
                    ResultSetMetaData md = rs.getMetaData();
                    while (rs.next()) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 1; i <= md.getColumnCount(); i++) {
                            sb.append(md.getColumnLabel(i)).append("=").append(rs.getString(i));
                            if (i < md.getColumnCount()) sb.append(" | ");
                        }
                        System.out.println(sb);
                    }
                }
            } else {
                System.out.println("affected: " + st.getUpdateCount());
            }
        }
    }
}
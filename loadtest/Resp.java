import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** 极简 Redis 客户端：java Resp <CMD> <arg...>  */
public class Resp {
    static Socket s;
    static BufferedInputStream in;
    static OutputStream out;

    public static void main(String[] args) throws Exception {
        s = new Socket("192.168.150.101", 6379);
        in = new BufferedInputStream(s.getInputStream());
        out = s.getOutputStream();
        cmd("AUTH", "123321");
        String r = cmd(args);
        System.out.println(r);
        s.close();
    }

    static String cmd(String... parts) throws IOException {
        StringBuilder sb = new StringBuilder("*").append(parts.length).append("\r\n");
        for (String p : parts) {
            sb.append("$").append(p.getBytes(StandardCharsets.UTF_8).length).append("\r\n").append(p).append("\r\n");
        }
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
        return readReply();
    }

    static String readReply() throws IOException {
        int t = in.read();
        if (t == -1) return "(eof)";
        String line = readLine();
        switch (t) {
            case '+': return line;
            case '-': return "(error) " + line;
            case ':': return line;
            case '$': {
                int len = Integer.parseInt(line);
                if (len < 0) return "(nil)";
                byte[] buf = new byte[len + 2];
                int read = 0;
                while (read < buf.length) {
                    int n = in.read(buf, read, buf.length - read);
                    if (n < 0) break;
                    read += n;
                }
                return new String(buf, 0, len, StandardCharsets.UTF_8);
            }
            case '*': {
                int n = Integer.parseInt(line);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    sb.append(readReply());
                    if (i < n - 1) sb.append("\n");
                }
                return sb.toString();
            }
            default: return "(?) " + t;
        }
    }

    static String readLine() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\r') { in.read(); break; }
            bos.write(c);
        }
        return new String(bos.toByteArray(), StandardCharsets.UTF_8);
    }
}
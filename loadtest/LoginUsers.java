import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/** 批量登录并输出 JMeter 用 CSV：java LoginUsers <count> <outFile> */
public class LoginUsers {
    public static void main(String[] args) throws Exception {
        int count = Integer.parseInt(args[0]);
        String out = args[1];
        String base = System.getProperty("base", "http://192.168.150.101:10010");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Future<String[]>> futures = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            final int n = i;
            futures.add(pool.submit(() -> {
                String phone = String.format("139%08d", n);
                String json = "{\"type\":1,\"cellPhone\":\"" + phone + "\",\"password\":\"Test@123\"}";
                HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/as/accounts/login"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(15))
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                String body = resp.body();
                int idx = body.indexOf("\"data\":\"");
                if (idx < 0) {
                    System.out.println("FAIL " + phone + " " + body);
                    return new String[]{phone, ""};
                }
                int start = idx + 8;
                int end = body.indexOf('"', start);
                return new String[]{phone, body.substring(start, end)};
            }));
        }
        int ok = 0;
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8))) {
            pw.println("phone,token");
            for (Future<String[]> f : futures) {
                String[] r = f.get();
                if (r[1].isEmpty()) continue;
                pw.println(r[0] + "," + r[1]);
                ok++;
            }
        }
        pool.shutdown();
        System.out.println("logins ok: " + ok + " -> " + out);
    }
}
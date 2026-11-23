import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PwCheck {
    public static void main(String[] args) {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        String hash = args[0];
        for (int i = 1; i < args.length; i++) {
            System.out.println(args[i] + " -> " + enc.matches(args[i], hash));
        }
        if (args.length == 1) {
            System.out.println(enc.encode("Test@123"));
        }
    }
}
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 独立运行，生成 BCrypt 密文。不依赖 Spring 容器。
 */
public class GenPwd {
    public static void main(String[] args) {
        BCryptPasswordEncoder enc = new BCryptPasswordEncoder();
        for (String pwd : new String[]{"123456", "admin123", "operator123"}) {
            String hash = enc.encode(pwd);
            System.out.println(pwd + " => " + hash + " (verify=" + enc.matches(pwd, hash) + ")");
        }
    }
}
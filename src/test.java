import javax.crypto.Cipher;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class test {

    public void main(String[] args) throws Exception{
        Set<String> s1 = new HashSet<>(Arrays.asList("a", "b"));
        Set<String> s2 = new HashSet<>(Arrays.asList("a", "b"));
        Cipher c = Cipher.getInstance("AES");
        int a = 1;
    }

}
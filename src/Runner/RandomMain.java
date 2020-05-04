//package Runner;
//
//import com.Constant;
//import com.changedistiller.test.SSLDetect.MultiLineDetection;
//import com.ibm.wala.ipa.cha.ClassHierarchyException;
//import com.ibm.wala.util.CancelException;
//
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.io.PrintStream;
//
//public class RandomMain {
//
//    public static void main(String []args) throws IOException {
//        PrintStream stream = new PrintStream(new FileOutputStream("Random_output.txt", true));
//        System.setOut(stream);
//        System.setErr(stream);
//        String jarfile = args[0];
//        String filePath = args[1];
//        try {
//            System.out.println(jarfile + " " + filePath);
//            MultiLineDetection detection = new MultiLineDetection();
//            detection.run(jarfile, filePath, "<init>", "SecureRandom");
//        } catch (Throwable e) {
//            return;
//        }
//
//    }
//}

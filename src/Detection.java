import com.ibm.wala.examples.drivers.PDFSlice;
import com.ibm.wala.util.CancelException;

import java.io.IOException;

public class Detection {
    public String filepath = "rigorityj-samples-1.0-SNAPSHOT.jar";

    public void run() throws CancelException, IOException {
        String path = "-appJar C:\\Users\\ying\\Documents\\JAVA_CODE\\cryptoapi-bench\\build\\libs\\rigorityj-samples-1.0-SNAPSHOT.jar ";
        //String path = "-appJar C:\\Users\\ling\\Documents\\JAVA_CODE\\cryptoapi-bench\\classes\\artifacts\\rigorityj_samples_jar ";
         String mainClass = "-mainClass Lorg/cryptoapi/bench/ecbcrypto/EcbInSymmCryptoABICase1 ";
        //String mainClass = "-mainClass Lorg/cryptoapi/bench/brokencrypto/BrokenCryptoABICase1 ";
        //这个参数没用了，现在会自己去搜函数在哪
        String caller = "-srcCaller main ";
        String callee = "-srcCallee getInstance ";
        String settings = "-full none -cd none -dir backward";

        PDFSlice pdfSlice = new PDFSlice(path+mainClass+callee+caller+settings, "Cipher");
        pdfSlice.run();

        pdfSlice.parameterList.stream().forEach(x -> System.out.println(x));
    }
}

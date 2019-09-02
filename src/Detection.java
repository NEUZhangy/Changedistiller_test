import com.ibm.wala.examples.slice.FlowTest;
import com.ibm.wala.examples.slice.MultiClass;
import com.ibm.wala.examples.slice.PDFSlice;
import com.ibm.wala.examples.slice.StaticField;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;

import java.io.IOException;

public class Detection {
    public String filepath = "rigorityj-samples-1.0-SNAPSHOT.jar";

    public void run() throws CancelException, IOException {
        String path = "-appJar C:\\Users\\ying\\Documents\\JAVA_CODE\\cryptoapi-bench\\build\\libs\\rigorityj-samples-1.0-SNAPSHOT.jar ";
        //String path = "-appJar C:\\Users\\ling\\Documents\\JAVA_CODE\\cryptoapi-bench\\classes\\artifacts\\rigorityj_samples_jar ";
        String mainClass ="-mainClass Lorg/cryptoapi/bench/predictablecryptographickey/PredictableCryptographicKeyBBCase1 ";
       // String mainClass = "-mainClass Lorg/cryptoapi/bench/brokencrypto/BrokenCryptoABICase2 ";
        //这个参数没用了，现在会自己去搜函数在哪
        String caller = "-srcCaller main ";
        String callee = "-srcCallee <init> ";
        String settings = "-full none -cd none -dir backward";

        PDFSlice pdfSlice = new PDFSlice(path+mainClass+callee+caller+settings, "SecretKeySpec");
        pdfSlice.run();

        pdfSlice.parameterList.stream().forEach(x -> System.out.println(x));
    }

    public void runSize() throws CancelException, IOException{
        //System.out.println("aaa");
        String path = "-appJar C:\\Users\\ying\\Documents\\JAVA_CODE\\cryptoapi-bench\\rigorityj-samples.jar ";
        //String path = "-appJar C:\\Users\\ling\\Documents\\JAVA_CODE\\cryptoapi-bench\\classes\\artifacts\\rigorityj_samples_jar ";
        //String mainClass = "-mainClass Lorg/cryptoapi/bench/predictableseeds/PredictableSeedsABICase2 ";
       // String mainClass ="-mainClass Lorg/cryptoapi/bench/pbeiteration/lessThan1000IterationPBEABHCase1 ";

        // test for the define statement//
       //String mainClass ="-mainClass Lorg/cryptoapi/bench/predictablecryptographickey/PredictableCryptographicKeyABHCase2 ";
        String mainClass ="-mainClass Lorg/cryptoapi/bench/brokencrypto/BrokenCryptoABSCase1 ";
        //String mainClass = "-mainClass Lorg/cryptoapi/bench/brokencrypto/BrokenCryptoABICase3 ";
        //这个参数没用了，现在会自己去搜函数在哪
        String caller = "-srcCaller main ";
        String callee = "-srcCallee getInstance ";
        String settings = "-dd NO_BASE_NO_HEAP_NO_EXCEPTIONS -cd full -dir backward";

        MultiClass multiClass = new MultiClass(path+mainClass+callee+caller+settings, "Cipher");
        //pdfSlice.runInit(path, mainClass,caller,callee,);
        multiClass.run();
    }

    public void runParam() throws CancelException, IOException{
        //System.out.println("aaa");
        String path = "-appJar C:\\Users\\ying\\Documents\\JAVA_CODE\\cryptoapi-bench\\rigorityj-samples.jar ";
        //String path = "-appJar C:\\Users\\ling\\Documents\\JAVA_CODE\\cryptoapi-bench\\classes\\artifacts\\rigorityj_samples_jar ";
        //String mainClass = "-mainClass Lorg/cryptoapi/bench/predictableseeds/PredictableSeedsBBCase1 "; //arraystore
        //String mainClass = "-mainClass Lorg/cryptoapi/bench/staticinitializationvector/StaticInitializationVectorBBCase1 ";
        //String mainClass = "-mainClass Lorg/cryptoapi/bench/predictablepbepassword/PredictablePBEPasswordBBCase2 ";
        //String mainClass = "-mainClass Lorg/cryptoapi/bench/predictablecryptographickey/PredictableCryptographicKeyABICase1 ";
        //String mainClass ="-mainClass Lorg/cryptoapi/bench/pbeiteration/PredictableKeyStorePasswordABICase1 ";
         String mainClass ="-mainClass Lorg/cryptoapi/bench/brokencrypto/BrokenCryptoABSCase1 ";
        //String mainClass ="-mainClass Lorg/cryptoapi/bench/ecbcrypto/EcbInSymmCryptoBBCase1 ";
        //String mainClass ="-mainClass Lorg/cryptoapi/bench/staticsalts/StaticSaltsABICase1 ";
        // test for the define statement//
        //String mainClass ="-mainClass Lorg/cryptoapi/bench/predictablecryptographickey/PredictableCryptographicKeyABHCase2 ";
        //String mainClass = "-mainClass Lorg/cryptoapi/bench/brokencrypto/BrokenCryptoABSCase1 ";
        //这个参数没用了，现在会自己去搜函数在哪
        String caller = "-srcCaller main ";
        //String callee = "-srcCallee <init> ";
       //String callee = "-srcCallee getInstance ";
        String callee = "-srcCallee getInstance ";
       String settings = "-dd NO_BASE_NO_HEAP_NO_EXCEPTIONS -cd full -dir backward";
        //String settings = "-dd none -cd none -dir backward";
        MultiClass paramSlice = new MultiClass(path+mainClass+callee+caller+settings, "Cipher");
        paramSlice.run();
    }

    public void runStatic() throws CancelException, IOException{
        //System.out.println("aaa");
        String path = "-appJar C:\\Users\\ying\\Documents\\JAVA_CODE\\cryptoapi-bench\\rigorityj-samples.jar ";
        //String path = "-appJar C:\\Users\\ling\\Documents\\JAVA_CODE\\cryptoapi-bench\\classes\\artifacts\\rigorityj_samples_jar ";
         //String mainClass = "-mainClass Lorg/cryptoapi/bench/predictableseeds/PredictableSeedsABICase2 ";
        // String mainClass ="-mainClass Lorg/cryptoapi/bench/pbeiteration/lessThan1000IterationPBEABHCase1 ";
//        String mainClass ="-mainClass Lorg/cryptoapi/bench/ecbcrypto/EcbInSymmCryptoBBCase1 ";
//        String mainClass = "-mainClass Lorg/cryptoapi/bench/predictableseeds/PredictableCryptographicKeyABHCase2 ";
        //String mainClass = "-mainClass Lorg/cryptoapi/bench/insecureasymmetriccrypto/InsecureAsymmetricCipherABICase2 ";
        //String mainClass ="-mainClass Lorg/cryptoapi/bench/staticsalts/StaticSaltsABICase2 ";
        //String mainClass = "-mainClass Lorg/cryptoapi/bench/brokencrypto/BrokenCryptoABICase10 ";
        // test for the define statement//
        //String mainClass ="-mainClass Lorg/cryptoapi/bench/predictablecryptographickey/PredictableCryptographicKeyABHCase2 ";
         //String mainClass = "-mainClass Lorg/cryptoapi/bench/brokenhash/BrokenHashABICase5 ";
        String mainClass = "-mainClass Lorg/cryptoapi/bench/brokencrypto/DummyTest ";
        //这个参数没用了，现在会自己去搜函数在哪
        String caller = "-srcCaller main ";
        //String callee = "-srcCallee <init> ";
        String callee = "-srcCallee getInstance ";
        String settings = "-dd NO_HEAP_NO_EXCEPTIONS -cd full -dir backward";
        //String settings = "-dd none -cd none -dir backward";
        StaticField StaticPara = new StaticField(path+mainClass+callee+caller+settings, "Cipher");
        StaticPara.run();
        StaticPara.paramList.stream().forEach(x -> System.out.println(x));
    }

    public void runFlowTest() throws CancelException, IOException, ClassHierarchyException {
        //System.out.println("aaa");
        String path = "-appJar C:\\Users\\ying\\Documents\\JAVA_CODE\\cryptoapi-bench\\build\\libs\\rigorityj-samples-1.0-SNAPSHOT.jar ";
        //String path = "-appJar C:\\Users\\ling\\Documents\\JAVA_CODE\\cryptoapi-bench\\classes\\artifacts\\rigorityj_samples_jar ";
//        String mainClass = "-mainClass Lorg/cryptoapi/bench/ecbcrypto/EcbInSymmCryptoABICase1 ";
        //String mainClass = "-mainClass Lorg/cryptoapi/bench/predictableseeds/PredictableSeedsABICase2 ";
        // String mainClass ="-mainClass Lorg/cryptoapi/bench/pbeiteration/lessThan1000IterationPBEABHCase1 ";
        //String mainClass ="-mainClass Lorg/cryptoapi/bench/ecbcrypto/EcbInSymmCryptoBBCase1 ";
       // String mainClass ="-mainClass Lorg/cryptoapi/bench/staticsalts/StaticSaltsABICase2 ";
        // test for the define statement//
        //String mainClass ="-mainClass Lorg/cryptoapi/bench/predictablecryptographickey/PredictableCryptographicKeyABHCase2 ";
        String mainClass = "-mainClass Lorg/cryptoapi/bench/brokencrypto/BrokenCryptoABICase5 ";
        //这个参数没用了，现在会自己去搜函数在哪
        String caller = "-srcCaller main ";
//        String callee = "-srcCallee <init> ";
        String callee = "-srcCallee getInstance ";
        String settings = "-dd full -cd full -dir backward";
        //String settings = "-dd none -cd none -dir backward";
        FlowTest flowtest = new FlowTest(path+mainClass+callee+caller+settings, "Cipher");
        flowtest.run();
        flowtest.paramList.stream().forEach(x -> System.out.println(x));
    }

}

package com.changedistiller.test.SSLDetect;

import com.Constant;
import com.ibm.wala.util.CancelException;
import org.junit.Test;

import java.io.IOException;

public class MultiLineDetectionTest {

    @Test
    public void run() throws CancelException, IOException {
        String path = "-appJar " + Constant.FILEPATH + " ";
        String mainClass ="-mainClass Lorg/cryptoapi/bench/dummyhostnameverifier/HostnameVerifierCase2 ";
        String caller = "-srcCaller main ";
        String callee = "-srcCallee getInstance ";
        String settings = "-dd full -cd full -dir forward";

        MultiLineDetection multiClass = new MultiLineDetection(path+mainClass+callee+caller+settings, "KeyPairGenerator");
        //pdfSlice.runInit(path, mainClass,caller,callee,);
        multiClass.run();
    }
}
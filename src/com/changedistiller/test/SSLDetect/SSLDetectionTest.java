package com.changedistiller.test.SSLDetect;

import com.changedistiller.test.FiletoAST;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.Test;

import java.io.File;

public class SSLDetectionTest {

    @Test
    public void detect() throws Exception {
        String lfilename = "DummyCertValidationCase1.java";
        File left = new File("E:\\Code\\Java\\cryptoapi-bench\\src\\main\\java\\org\\cryptoapi\\bench\\dummycertvalidation\\" + lfilename);
        String[] lsourcepath = {"E:\\Code\\Java\\cryptoapi-bench\\src\\main\\java\\org\\cryptoapi\\bench\\dummycertvalidation"};
        FiletoAST leftast = new FiletoAST(lsourcepath, left, lfilename);
        CompilationUnit lcu = leftast.getComplicationUnit();
        SSLDetection detection = new SSLDetection();
        detection.detect(lcu);
    }
}
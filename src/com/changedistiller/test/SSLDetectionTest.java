package com.changedistiller.test;

import org.eclipse.jdt.core.dom.CompilationUnit;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class SSLDetectionTest {

    @Test
    public void detect() throws Exception {
        String lfilename = "checkclient.java";
        File left = new File("C:\\Users\\Ying\\Documents\\JAVA_CODE\\cipher_test\\src\\cipher_test\\insecure\\SSL\\" + lfilename);
        String[] lsourcepath = {"C:\\Users\\Ying\\Documents\\JAVA_CODE\\cipher_test\\src\\cipher_test"};
        FiletoAST leftast = new FiletoAST(lsourcepath, left, lfilename);
        CompilationUnit lcu = leftast.getComplicationUnit();
        SSLDetection detection = new SSLDetection();
//        detection.detect(lcu);
    }
}
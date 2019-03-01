package com.changedistiller.test;

import org.eclipse.jdt.core.dom.*;
import java.io.File;

public class Main {

    public static void main(String[] args) throws Exception {

        File left = new File("D:\\work\\Java\\cipher_test\\src\\cipher_test\\SampleCipher_insecure.java");
        String lfilename = "SampleCipher_insecure.java";
        String lsourcepath[] = {"D:\\work\\Java\\cipher_test\\src\\cipher_test"};
        File right = new File("D:\\work\\Java\\cipher_test\\src\\cipher_test\\SampleCipher.java");
        String rsourcepath[] = {"D:\\work\\Java\\cipher_test\\src\\cipher_test"};
        String rfilename = "SampleCipher.java";
        FiletoAST leftast = new FiletoAST(lsourcepath, left, lfilename);
        FiletoAST rightast = new FiletoAST(rsourcepath, right, rfilename);
        CompilationUnit lcu = leftast.getComplicationUnit();
        CompilationUnit rcu = rightast.getComplicationUnit();
        GeneratePattern gp = new GeneratePattern();
        gp.Compare(left, right, lcu, rcu);
//        gp.LoadFromFile("pattern.ser");

    }
}


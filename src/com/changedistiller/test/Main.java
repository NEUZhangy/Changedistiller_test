package com.changedistiller.test;

import org.eclipse.jdt.core.dom.CompilationUnit;

import java.io.File;

public class Main {

    public static void main(String[] args) throws Exception {

        String lfilename = "1.java";
        String rfilename = "1.java";

        File left = new File("C:\\Users\\Ling\\Documents\\JAVA_CODE\\cipher_test\\src\\cipher_test\\insecure\\parameter\\" + lfilename);
        String lsourcepath[] = {"C:\\Users\\Ling\\Documents\\JAVA_CODE\\cipher_test\\src\\cipher_test"};
        File right = new File("C:\\Users\\Ling\\Documents\\JAVA_CODE\\cipher_test\\src\\cipher_test\\secure\\parameter\\" + rfilename);
        String rsourcepath[] = {"C:\\Users\\Ling\\Documents\\JAVA_CODE\\cipher_test\\src\\cipher_test"};
        FiletoAST leftast = new FiletoAST(lsourcepath, left, lfilename);
        FiletoAST rightast = new FiletoAST(rsourcepath, right, rfilename);
        CompilationUnit lcu = leftast.getComplicationUnit();
        CompilationUnit rcu = rightast.getComplicationUnit();
        GeneratePattern gp = new GeneratePattern();
        gp.Compare(left, right, lcu, rcu);
    }
}


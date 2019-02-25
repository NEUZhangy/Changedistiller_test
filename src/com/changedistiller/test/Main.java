package com.changedistiller.test;

import ch.uzh.ifi.seal.changedistiller.ChangeDistiller;
import ch.uzh.ifi.seal.changedistiller.distilling.FileDistiller;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.SourceRange;
import ch.uzh.ifi.seal.changedistiller.model.entities.*;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.Node;
import edu.vt.cs.append.FineChangesInMethod;
import edu.vt.cs.append.JavaExpressionConverter;
import edu.vt.cs.append.terms.VariableTypeBindingTerm;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import java.io.File;
import java.util.*;

import org.apache.commons.io.*;

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


    }
}


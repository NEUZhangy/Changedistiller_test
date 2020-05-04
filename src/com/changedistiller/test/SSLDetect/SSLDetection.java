package com.changedistiller.test;

import org.eclipse.jdt.core.dom.*;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

public class SSLDetection {

    public void detect(CompilationUnit cu, String filename) throws FileNotFoundException {
        PrintStream stream = new PrintStream(new FileOutputStream("SSLoutput.txt", true));
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                if (node.getName().toString().contains("checkClientTrusted") ||
                node.getName().toString().contains("checkServerTrusted")){
                   if (node.getBody().statements().size() == 0 || node.thrownExceptionTypes().size() == 0) {
                       stream.println(filename + ": " + node.getName().toString() + " insecure");
                   }
                }
                if (node.getName().toString().contains("verify")) {
                    if (node.getBody().statements().size() == 1 || node.thrownExceptionTypes().size() == 0) {
                        stream.println(filename + ": " + node.getName().toString() + " insecure");
                    }
                }

                return super.visit(node);
            }
        });

    }
}

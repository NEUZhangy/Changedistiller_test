package com.changedistiller.test.SSLDetect;

import org.eclipse.jdt.core.dom.*;

public class SSLDetection {

    public void detect(CompilationUnit cu) {
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                if (node.getName().toString().contains("checkClientTrusted") ||
                node.getName().toString().contains("checkServerTrusted")){
                   if (node.getBody().statements().size() == 0 || node.thrownExceptionTypes().size() == 0) {
                       System.out.println(node.getName().toString() + " insecure");
                   }
                }
                return super.visit(node);

            }
        });

    }
}

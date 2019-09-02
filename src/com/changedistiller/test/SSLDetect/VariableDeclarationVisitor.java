package com.changedistiller.test.SSLDetect;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.Map;

public class VariableDeclarationVisitor extends VoidVisitorAdapter<Map<String, String>> {
    int trace = 0;

    @Override
    public void visit(ClassOrInterfaceDeclaration n, Map<String, String> arg) {
        super.visit(n, arg);
        n.getFields().stream().forEach(x -> {
            x.getVariables().stream().forEach(a-> arg.put(a.getNameAsString(), "$v" + "_" + Integer.toString(trace++)));
        });
    }

    @Override
    public void visit(VariableDeclarationExpr n, Map<String, String> arg) {
        super.visit(n, arg);
        n.getVariables().stream().forEach(x -> arg.put(x.getNameAsString(), "$v" + "_" + Integer.toString(trace++)));
    }
}

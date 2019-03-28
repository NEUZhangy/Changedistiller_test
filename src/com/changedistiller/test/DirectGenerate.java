package com.changedistiller.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class DirectGenerate {

    private CompilationUnit cu;

    public DirectGenerate() throws Exception {
        JavaParser jp = new JavaParser();
        TypeSolver typeSolver = new ReflectionTypeSolver();
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        JavaParser.getStaticConfiguration().setSymbolResolver(symbolSolver);
        String file_path = "src/test.java";
        cu = jp.parse(new File(file_path));
        List<Statement> statementList = new ArrayList<>();
        cu.findAll(MethodCallExpr.class).forEach(ae -> {
            ResolvedType resolvedType = ae.calculateResolvedType();
            System.out.println(ae.resolveInvokedMethod().getQualifiedSignature());
            ae.getArguments().stream().forEach(x -> System.out.println(x));
        });


    }

}

package com.changedistiller.test;

import com.github.javaparser.JavaParser;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

public class DirectGenerate {

    private CompilationUnit cu;

    public DirectGenerate(String file_path) throws Exception {
        TypeSolver typeSolver = new ReflectionTypeSolver();
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);
        file_path = "src/test.java";
        cu = StaticJavaParser.parse(new File(file_path));
        List<MethodCallExpr> statementList = new ArrayList<>();
        AtomicReference<String> binding = new AtomicReference<>("");
        cu.findAll(MethodCallExpr.class).forEach(ae -> {
            ResolvedType resolvedType = ae.calculateResolvedType();
            //binding.set(ae.resolveInvokedMethod().getQualifiedSignature());
            ae.getArguments().stream().forEach(x -> System.out.println(x));
            if (ae.getNameAsString().equals("asList")) {
                statementList.add(ae);
            }
        });
        int pos = 0;
        String name = binding.get() + "_" + pos;
        String matchExpression = name;
        int type = 0;
        Set<String> iSet = new HashSet<>();
        Set<String> cSet = new HashSet<>();
        statementList.get(0).getArguments().stream().forEach(x -> iSet.add(x.toString()));
        statementList.get(1).getArguments().stream().forEach(x -> cSet.add(x.toString()));

        System.out.println(iSet);
        System.out.println(cSet);
    }

}

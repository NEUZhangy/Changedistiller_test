package com.changedistiller.test;

import org.eclipse.jdt.core.dom.*;

import java.util.*;

public class CompositePattern implements CodePattern{

    private int count = 0;
    private String name;
    private Map <String,String> variableMap = new HashMap<>();
    private List<String> lcuTemplateStatements = new ArrayList<String>();
    private List<String> rcuTemplateStatements = new ArrayList<String>();

    CompositePattern(CompilationUnit lcu, CompilationUnit rcu){

        lcu.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                VariableDeclarationFragment vdf = (VariableDeclarationFragment) node.fragments().get(0);
                if (!variableMap.containsKey(vdf.getName().toString())) {
                    variableMap.put(vdf.getName().toString(), new String().format("v_%d", count));
                    System.out.println(vdf.getName().toString() + new String().format("v_%d", count));
                    count++;
                }
                String templateStatement = node.toString();
                for (Map.Entry<String, String> entry: variableMap.entrySet()) {
                    templateStatement = templateStatement.replaceAll(entry.getKey(), entry.getValue());
                }
                lcuTemplateStatements.add(templateStatement);
                return super.visit(node);
            }

            public boolean visit(ExpressionStatement node) {
                String templateStatement = node.toString();
                for (Map.Entry<String, String> entry: variableMap.entrySet()) {
                    templateStatement = templateStatement.replaceAll(entry.getKey(), entry.getValue());
                }
                lcuTemplateStatements.add(templateStatement);
                return super.visit(node);
            }
        });
        rcu.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                VariableDeclarationFragment vdf = (VariableDeclarationFragment) node.fragments().get(0);
                if (!variableMap.containsKey(vdf.getName().toString())) {
                    variableMap.put(vdf.getName().toString(), new String().format("v_%d", count));
                    System.out.println(vdf.getName().toString() + new String().format("v_%d", count));
                    count++;
                }
                String templateStatement = node.toString();
                for (Map.Entry<String, String> entry: variableMap.entrySet()) {
                    templateStatement = templateStatement.replaceAll(entry.getKey(), entry.getValue());
                }
                rcuTemplateStatements.add(templateStatement);
                return super.visit(node);
            }

            public boolean visit(ExpressionStatement node) {
                String templateStatement = node.toString();
                for (Map.Entry<String, String> entry: variableMap.entrySet()) {
                    templateStatement = templateStatement.replaceAll(entry.getKey(), entry.getValue());
                }
                rcuTemplateStatements.add(templateStatement);
                System.out.println(templateStatement);
                return super.visit(node);
            }
        });
    }

    public List<String> getLcuTemplateStatements() {
        return lcuTemplateStatements;
    }

    public List<String> getRcuTemplateStatements() {
        return rcuTemplateStatements;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

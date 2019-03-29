package com.changedistiller.test;

import org.eclipse.jdt.core.dom.*;

import java.util.*;

public class CompositePattern implements CodePattern{

    private int varCount = 0;
    private int constantCount = 0;
    private String name;
    private Map <String, String> variableMap = new HashMap<>();
    private Map <String, String> constantMap = new HashMap<>();
    private List<String> lcuTemplateStatements = new ArrayList<String>();
    private List<String> rcuTemplateStatements = new ArrayList<String>();

    CompositePattern(CompilationUnit lcu, CompilationUnit rcu){

        lcu.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                VariableDeclarationFragment vdf = (VariableDeclarationFragment) node.fragments().get(0);
                if (!variableMap.containsKey(vdf.getName().toString())) {
                    variableMap.put(vdf.getName().toString(), new String().format("\\$v_%d", varCount));
                    System.out.println(vdf.getName().toString() + new String().format("\\$v_%d", varCount));
                    varCount++;
                }
                vdf.getInitializer().accept(new ASTVisitor() {
                    @Override
                    public boolean visit(MethodInvocation node) {
                        if (node.getExpression() instanceof StringLiteral) {
                            constantMap.put(node.getExpression().toString(), new String().format("\\$c_%d", constantCount));
                            constantCount++;
                        }
                        return super.visit(node);
                    }
                });

                lcuTemplateStatements.add(GenerateTemplateString(node.toString()));
                return super.visit(node);
            }

            public boolean visit(ExpressionStatement node) {
                lcuTemplateStatements.add(GenerateTemplateString(node.toString()));
                return super.visit(node);
            }
        });
        rcu.accept(new ASTVisitor() {
            @Override
            public boolean visit(VariableDeclarationStatement node) {
                VariableDeclarationFragment vdf = (VariableDeclarationFragment) node.fragments().get(0);
                if (!variableMap.containsKey(vdf.getName().toString())) {
                    variableMap.put(vdf.getName().toString(), new String().format("\\$v_%d", varCount));
                    varCount++;
                }
                vdf.getInitializer().accept(new ASTVisitor() {
                    @Override
                    public boolean visit(MethodInvocation node) {
                        if (node.getExpression() instanceof StringLiteral) {
                            constantMap.put(node.getExpression().toString(), new String().format("\\$c_%d", constantCount));
                            constantCount++;
                        }
                        return super.visit(node);
                    }
                });
                rcuTemplateStatements.add(GenerateTemplateString(node.toString()));
                return super.visit(node);
            }

            public boolean visit(ExpressionStatement node) {
                rcuTemplateStatements.add(GenerateTemplateString(node.toString()));
                return super.visit(node);
            }
        });

        setName(Integer.toString(lcuTemplateStatements.hashCode()));
    }

    public List<String> getLcuTemplateStatements() { return lcuTemplateStatements; }

    public List<String> getRcuTemplateStatements() {
        return rcuTemplateStatements;
    }

    public String GenerateTemplateString(String templateStatement) {
        for (Map.Entry<String, String> entry: variableMap.entrySet()) {
            templateStatement = templateStatement.replaceAll(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry: constantMap.entrySet()) {
            templateStatement = templateStatement.replaceAll(entry.getKey(), entry.getValue());
        }
        return templateStatement;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

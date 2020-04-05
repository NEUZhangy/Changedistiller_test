package com.changedistiller.test;

import org.eclipse.jdt.core.dom.*;
import org.json.simple.JSONObject;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CompositePattern implements CodePattern{

    private int varCount = 0;
    private int constantCount = 0;
    private String name;
    private String matchingExpression;
    private Map <String, String> variableMap = new HashMap<>();
    private Map <String, String> constantMap = new HashMap<>();
    private List<String> lcuTemplateStatements = new ArrayList<String>();
    private List<String> rcuTemplateStatements = new ArrayList<String>();

    CompositePattern(ASTNode lcu, ASTNode rcu){

        if(lcu != null) {
            lcu.accept(new ASTVisitor() {
                @Override
                public boolean visit(VariableDeclarationStatement node) {
                    VariableDeclarationFragment vdf = (VariableDeclarationFragment) node.fragments().get(0);
                    if (!variableMap.containsKey(vdf.getName().toString())) {
                        variableMap.put(vdf.getName().toString(), String.format("\\$v_%d", varCount));
                        System.out.println(vdf.getName().toString() + String.format("\\$v_%d", varCount));
                        varCount++;
                    }
                    vdf.getInitializer().accept(new ASTVisitor() {
                        @Override
                        public boolean visit(MethodInvocation node) {
                            if (node.getExpression() instanceof StringLiteral) {
                                constantMap.put(node.getExpression().toString(), String.format("\\$c_%d", constantCount));
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

                public boolean visit(IfStatement node){
                    lcuTemplateStatements.add(GenerateTemplateString(node.toString()));
                    return super.visit(node);
                }

                public boolean visit(TryStatement node){
                    lcuTemplateStatements.add(GenerateTemplateString(node.toString()));
                    return false;
                }


                public boolean visit(MethodDeclaration node) {
                    for (Object para:node.parameters()) {
                        if (para instanceof SingleVariableDeclaration) {
                            if (!variableMap.containsKey(para.toString())) {
                                variableMap.put(((SingleVariableDeclaration) para).getName().toString(), String.format("\\$v_%d", varCount));
                                // System.out.println(((SingleVariableDeclaration) para).getName().toString() + new String().format("\\$v_%d", varCount));
                                varCount++;
                            }
                        }
                    }
                    return super.visit(node);
                }
            });
        }

        if (rcu != null) {
            rcu.accept(new ASTVisitor() {
                @Override
                public boolean visit(VariableDeclarationStatement node) {
                    VariableDeclarationFragment vdf = (VariableDeclarationFragment) node.fragments().get(0);
                    if (!variableMap.containsKey(vdf.getName().toString())) {
                        variableMap.put(vdf.getName().toString(), String.format("\\$v_%d", varCount));
                        varCount++;
                    }
                    vdf.getInitializer().accept(new ASTVisitor() {
                        @Override
                        public boolean visit(MethodInvocation node) {
                            if (node.getExpression() instanceof StringLiteral) {
                                constantMap.put(node.getExpression().toString(), String.format("\\$c_%d", constantCount));
                                constantCount++;
                            }
                            return super.visit(node);
                        }
                    });
                    rcuTemplateStatements.add(GenerateTemplateString(node.toString()));
                    return super.visit(node);
                }

                public boolean visit(IfStatement node){
                    rcuTemplateStatements.add(GenerateTemplateString(node.toString()));
                    return super.visit(node);
                }

                public boolean visit(TryStatement node){
                    rcuTemplateStatements.add(GenerateTemplateString(node.toString()));
                    return false;
                }
                public boolean visit(ExpressionStatement node) {
                    rcuTemplateStatements.add(GenerateTemplateString(node.toString()));
                    return super.visit(node);
                }

                public boolean visit(MethodDeclaration node) {
                    for (Object para:node.parameters()) {
                        if (para instanceof SingleVariableDeclaration) {
                            if (!variableMap.containsKey(para.toString())) {
                                variableMap.put(((SingleVariableDeclaration) para).getName().toString(), String.format("\\$v_%d", varCount));
                                //System.out.println(((SingleVariableDeclaration) para).getName().toString() + new String().format("\\$v_%d", varCount));
                                varCount++;
                            }
                        }
                    }
                    System.out.println(node.resolveBinding());
                    rcuTemplateStatements.add(GenerateTemplateString(node.toString()));
                    return false;
                }
            });
        }

        setName(Integer.toString(lcuTemplateStatements.hashCode()));
    }

    public List<String> getLcuTemplateStatements() { return lcuTemplateStatements; }

    public List<String> getRcuTemplateStatements() {
        return rcuTemplateStatements;
    }

    public String GenerateTemplateString(String templateStatement) {

        for (Map.Entry<String, String> entry: variableMap.entrySet()) {
            String regex = entry.getKey() + "[^a-zA-Z0-9_)]";
            Pattern p = Pattern.compile(regex);
            Matcher m = p.matcher(templateStatement);
            List<Character> remain = new ArrayList<>();
            while(m.find()) {
                remain.add(m.group(0).charAt(m.group(0).length()-1));
            }
            templateStatement = templateStatement.replaceAll(regex, entry.getValue());
            for (Character c: remain) {
                templateStatement = templateStatement.replaceFirst(entry.getValue(), entry.getValue() + c);
            }
        }
        for (Map.Entry<String, String> entry: constantMap.entrySet()) {
            templateStatement = templateStatement.replaceAll(entry.getKey() + "[^a-zA-Z0-9_]", entry.getValue());
        }
        return templateStatement;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMatchingExpression() {
        return matchingExpression;
    }

    public void setMatchingExpression(String matchingExpression) {
        this.matchingExpression = matchingExpression;
    }


    @Override
    public JSONObject marshall() {
        Map<String, String> jsonFields = new HashMap<>();
        jsonFields.put("Type", "composite");
        jsonFields.put("matchingExpression", this.matchingExpression);
        jsonFields.put("incorrectStmts", lcuTemplateStatements.toString());
        jsonFields.put("correctStmts", rcuTemplateStatements.toString());
        return new JSONObject(jsonFields);
    }
}

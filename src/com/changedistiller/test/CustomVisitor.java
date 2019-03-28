package com.changedistiller.test;

import org.eclipse.jdt.core.dom.*;

import java.util.List;

public class CustomVisitor extends ASTVisitor {

    public String bindingName;
    public String matchingExpression;

    public CustomVisitor() {}

    public void VisitTarget(ASTNode cu, List<ASTNode> targetnode, List<ASTNode> nodeArguments) {
        cu.accept(new ASTVisitor() {

            @Override
            public boolean visit(MethodInvocation node) {
                System.out.println("method invocation++:" + node);
                System.out.println("MI_information: " + node.resolveMethodBinding());
                System.out.println("test Va: "+ node.getExpression().resolveConstantExpressionValue());
//                constantExpression = node.getExpression().resolveConstantExpressionValue().toString();
                bindingName = node.resolveMethodBinding().toString();
                nodeArguments.addAll(node.arguments());
                StringBuilder sb = new StringBuilder();
                sb.append(new String().format("%s.%s", node.getExpression(), node.getName()));
                for (int i = 0; i < nodeArguments.size(); i++) {
                    if (i == 0) sb.append("(");
                    if (i != 0) sb.append(",");
                    sb.append(GetVariableWildcardName(nodeArguments.get(i)));
                    if (i == nodeArguments.size() - 1) sb.append(")");
                }
                matchingExpression = sb.toString();
                System.out.println("matching Expression: " + matchingExpression);
                targetnode.add(node);
                return super.visit(node);
            }


            @Override
            public boolean visit(ClassInstanceCreation node){
                System.out.println("varible type: " + node);
                System.out.println("nodeType: " + node.getType());
                System.out.println("Binding: " + node.resolveTypeBinding());
                bindingName = node.getType().toString();
                targetnode.add(node);
                nodeArguments.addAll(node.arguments());
                System.out.println("Arguments: " + node.arguments());
                return super.visit(node);
            }

            //if it is a constant, we should discard it and save a $V_C in the template,
            @Override
            public boolean visit(VariableDeclarationFragment node){
//                System.out.println("test Va: "+ node.getInitializer().resolveConstantExpressionValue());
                StringBuilder sb = new StringBuilder(node.getName().toString());
                sb.append("=");
                if (node.getInitializer().getNodeType() == ASTNode.ARRAY_CREATION)
                    sb.append(GetVariableWildcardName(node.getInitializer()));
                bindingName = node.resolveBinding().getName();
                matchingExpression = sb.toString();
                System.out.println(matchingExpression);
                return super.visit(node);
            }

        });
    }

    public String GetVariableWildcardName(ASTNode node) {
        String wildCard;
        switch (node.getNodeType()) {
            case ASTNode.STRING_LITERAL:
                wildCard = "$sl";
                break;
            case ASTNode.NUMBER_LITERAL:
                wildCard = "$nl";
                break;
            case ASTNode.ARRAY_CREATION:
                wildCard = node.toString().replaceAll("\\[\\d+\\]", "\\[\\$nl\\]");
                break;
            default:
                wildCard = "";
                break;
        }
        return wildCard;
    }

}

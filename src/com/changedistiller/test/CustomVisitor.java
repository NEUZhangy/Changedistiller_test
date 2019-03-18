package com.changedistiller.test;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.MethodInvocation;

import java.util.List;

public class CustomVisitor extends ASTVisitor {

    public String bindingName;
    public String constantExpression;

    public CustomVisitor() {}

    public void VisitTarget(ASTNode cu, List<ASTNode> targetnode, List<?> nodeArguments) {
        cu.accept(new ASTVisitor() {

            @Override
            public boolean visit(MethodInvocation node) {
                System.out.println("method invocation++:" + node);
                System.out.println("MI_information: " + node.resolveMethodBinding());
                //System.out.println("test Va"+ node.getExpression().resolveConstantExpressionValue());
//                constantExpression = node.getExpression().resolveConstantExpressionValue().toString();
                bindingName = node.resolveMethodBinding().toString();
                targetnode.add(node);
                return super.visit(node);
            }


            @Override
            public boolean visit(ClassInstanceCreation node){
                System.out.println("varible type: " + node);
                System.out.println("nodeType: " + node.getType());
                bindingName = node.getType().toString();
                targetnode.add(node);
                nodeArguments.addAll(node.arguments());
                System.out.println("Arguments: " + node.arguments());
                return super.visit(node);
            }

        });
    }


}

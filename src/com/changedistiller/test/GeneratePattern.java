package com.changedistiller.test;

import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import org.eclipse.jdt.core.dom.*;
import org.json.simple.JSONObject;

import java.util.*;

public class GeneratePattern {

    private Map<String, String> pattern = new HashMap<>();
    private Set<String> methodtype = new HashSet<>();
    private List<CodePattern> patternlist = new ArrayList<CodePattern>();

    GeneratePattern(ASTNode node, SourceCodeChange change){
        JSONObject value = new JSONObject();
        value.put("operation","STATEMNET_UPDATE");
        value.put("insecurity", "MD5");
        value.put("security", "SHA256");
//        opattern.put(node,value);
    }

    GeneratePattern(){
        this.methodtype.add("public static final javax.crypto.Cipher getInstance");
    }

    public Map<String, String> getPattern()
    {
        return this.pattern;
    }

    public void VisitTarget(ASTNode cu, List<ASTNode> targetnode) {
        Set<MethodInvocation> node_sets;
        cu.accept(new ASTVisitor() {
//            @Override
//            public boolean visit(SimpleName node) {
//                System.out.println("identifier:" + node.getIdentifier());
//                System.out.println("binding information:" + node.resolveTypeBinding().getBinaryName());
//                IBinding fbinding = node.resolveBinding();
//                System.out.println(fbinding.getKind());
//                if (fbinding.getKind() == IBinding.METHOD) {
//                    IMethodBinding methodBinding = (IMethodBinding) fbinding;
//                    System.out.println("method binding: " + methodBinding);
//
//                }
//                return super.visit(node);
//            }

//            @Override
//            public boolean visit(StringLiteral node) {
//                System.out.println(node);
//                return super.visit(node);
//            }
//
//            @Override
//            public boolean visit(MethodDeclaration node) {
////                System.out.println("method:"+ node );
//                return super.visit(node);
//            }
            @Override
            public boolean visit(MethodInvocation node){
                //&& node.arguments().get(0).toString().equals("\"MD5\"")
                //if (methodtype.contains(node.resolveMethodBinding().toString())) {
                System.out.println("method invocation++:"+ node);
                System.out.println("MI_information: "+ node.resolveMethodBinding());

                //pattern.put(str_list, null);
                //targetnode.add(node);
               // }
                return super.visit(node);

            }
        });
    }


}

package com.changedistiller.test;

import ch.uzh.ifi.seal.changedistiller.ChangeDistiller;
import ch.uzh.ifi.seal.changedistiller.distilling.FileDistiller;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.SourceRange;
import ch.uzh.ifi.seal.changedistiller.model.entities.*;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.Node;
import edu.vt.cs.append.FineChangesInMethod;
import edu.vt.cs.append.JavaExpressionConverter;
import edu.vt.cs.append.terms.VariableTypeBindingTerm;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.*;
import java.io.File;
import java.util.*;

import org.apache.commons.io.*;

public class Main {

    public static void main(String[] args) throws Exception {

        File left = new File("D:\\work\\Java\\cipher_test\\src\\cipher_test\\SampleCipher_insecure.java");
        String lfilename = "SampleCipher_insecure.java";
        String lsourcepath[] = {"D:\\work\\Java\\cipher_test\\src\\cipher_test"};
        File right = new File("D:\\work\\Java\\cipher_test\\src\\cipher_test\\SampleCipher.java");
        String rsourcepath[] = {"D:\\work\\Java\\cipher_test\\src\\cipher_test"};
        String rfilename = "SampleCipher.java";
        FiletoAST leftast = new FiletoAST(lsourcepath, left, lfilename);
        FiletoAST rightast = new FiletoAST(rsourcepath, right, rfilename);
        CompilationUnit lcu = leftast.getComplicationUnit();
        CompilationUnit rcu = rightast.getComplicationUnit();

        FileDistiller distiller = ChangeDistiller.createFileDistiller();
        FileDistiller.setIgnoreComments();
        try {
            distiller.extractClassifiedSourceCodeChanges(left, right);
        } catch (Exception e) {
            System.out.println("warning" + e.getMessage());
        }

        //get the statement of changedistiller and oldfrang, newfrang
        List<SourceCodeChange> changes = distiller.getSourceCodeChanges();//the size of changes=1, why it is list type??
        List<SourceRange> srlist = new ArrayList<>();
        List<SourceRange> newsrlist  = new ArrayList<>();
        if (changes != null) {
            for (SourceCodeChange change : changes) {
                System.out.println("change operation"+ change);
                FineChangesInMethod fc = (FineChangesInMethod) change;
                List<SourceCodeChange> changelist =fc.getChanges();
                for (SourceCodeChange scc : fc.getChanges()) {
                    if(scc instanceof Update) {
                        srlist.add(scc.getChangedEntity().getSourceRange());
                        System.out.println(scc.getChangedEntity().getSourceRange());
                        System.out.println(((Update) scc).getNewEntity().getSourceRange()); //get frange
                        newsrlist.add(((Update) scc).getNewEntity().getSourceRange());
                    }

                    if(scc instanceof Insert){
                       System.out.println(scc.getChangedEntity().getSourceRange());
                       //System.out.println(((Insert) scc).getParentEntity().getSourceRange());
                       newsrlist.add(((Insert) scc).getChangedEntity().getSourceRange());
                    }

                    if(scc instanceof Delete) {
                        srlist.add(scc.getChangedEntity().getSourceRange());
                        System.out.println(scc.getChangedEntity().getSourceRange());
//                        System.out.println(((Delete) scc).getNewEntity().getSourceRange());
                    }
                }
            }
        }

        //get the AST node from list and filter by API
        List<ASTNode> ltargetnode =new ArrayList<>();
        List<ASTNode> rtargetnode = new ArrayList<>();
        GeneratePattern pa1 = new GeneratePattern();
        for (SourceRange l: srlist) {
            ASTNode ltmpNode =NodeFinder.perform(lcu.getRoot(),l.getStart(),l.getEnd()-l.getStart());
            pa1.VisitTarget(ltmpNode,ltargetnode);
        }
        for (SourceRange r: newsrlist){
            ASTNode rtmpNode =NodeFinder.perform(rcu.getRoot(),r.getStart(),r.getEnd()-r.getStart());
            pa1.VisitTarget(rtmpNode,rtargetnode);
        }

        //TODO : 遍历提出堆成的node
        JavaExpressionConverter leftConverter = new JavaExpressionConverter();
        ltargetnode.get(0).accept(leftConverter);
        Node leftRoot = leftConverter.getRoot();
//        JavaExpressionConverter.markSubStmts(leftRoot, ltargetnode);

        JavaExpressionConverter rightConverter = new JavaExpressionConverter();
        rtargetnode.get(0).accept(rightConverter);
        Node rightRoot = rightConverter.getRoot();

        //get the low level-differcing
        Enumeration<Node> lEnum = leftRoot.breadthFirstEnumeration();
        Enumeration<Node> rEnum = rightRoot.breadthFirstEnumeration();
        Map<String, String> lkeymapping =new HashMap<String, String>();
        Map<String, String> rkeymapping =new HashMap<String, String>();

        //get typeinformation
        for(String key: leftConverter.variableTypeMap.keySet()){
            String[] split = key.split("\\+");
            lkeymapping.put(split[0], key);
        }
        for(String key: rightConverter.variableTypeMap.keySet()){
            String[] split = key.split("\\+");
            rkeymapping.put(split[0], key);
        }

        while(lEnum.hasMoreElements() && rEnum.hasMoreElements()) {
            Node lcurNode = lEnum.nextElement();
            Node rcurNode = rEnum.nextElement();
            //get parameter differnece and binding information
            Map<String, String> operation = pa1.getPattern();
            if(!lcurNode.getValue().equals(rcurNode.getValue())) {
                System.out.println("leftdifferencing:"+lcurNode.getValue());
                System.out.println("rightdiffernt:"+ rcurNode.getValue());
                VariableTypeBindingTerm vtbt = leftConverter.variableTypeMap.get(lkeymapping.get(lcurNode.getValue()));
                System.out.println(vtbt);
            }

        }
        System.out.println();
    }
}


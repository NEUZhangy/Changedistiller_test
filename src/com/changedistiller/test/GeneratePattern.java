package com.changedistiller.test;

import ch.uzh.ifi.seal.changedistiller.ChangeDistiller;
import ch.uzh.ifi.seal.changedistiller.distilling.FileDistiller;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.SourceRange;
import ch.uzh.ifi.seal.changedistiller.model.entities.Delete;
import ch.uzh.ifi.seal.changedistiller.model.entities.Insert;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import ch.uzh.ifi.seal.changedistiller.model.entities.Update;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.Node;
import edu.vt.cs.append.FineChangesInMethod;
import edu.vt.cs.append.JavaExpressionConverter;
import edu.vt.cs.append.terms.VariableTypeBindingTerm;
import org.eclipse.jdt.core.dom.*;
import org.json.simple.JSONObject;

import java.io.*;
import java.util.*;

public class GeneratePattern {

    private Set<String> methodtype = new HashSet<>();
    private Map<String, CodePattern> patternMap = new HashMap<>();
    private File left;
    private File right;
    private CompilationUnit lcu;
    private CompilationUnit rcu;

//    GeneratePattern(ASTNode node, SourceCodeChange change){
//        JSONObject value = new JSONObject();
//        value.put("operation","STATEMNET_UPDATE");
//        value.put("insecurity", "MD5");
//        value.put("security", "SHA256");
////        opattern.put(node,value);
//    }

    GeneratePattern(){}

    GeneratePattern(File left, File right, CompilationUnit lcu, CompilationUnit rcu){
        this.left = left;
        this.right = right;
        this.lcu = lcu;
        this.rcu= rcu;
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
            public boolean visit(MethodInvocation node) {
                //&& node.arguments().get(0).toString().equals("\"MD5\"")
                //if (methodtype.contains(node.resolveMethodBinding().toString())) {
                System.out.println("method invocation++:" + node);
                System.out.println("MI_information: " + node.resolveMethodBinding());
                //pattern.put(str_list, null);
                targetnode.add(node);
                // }
                return super.visit(node);
            }
        });
    }

    public void Compare(File left, File right, CompilationUnit lcu, CompilationUnit rcu){
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
            for (SourceRange l: srlist) {
                ASTNode ltmpNode =NodeFinder.perform(lcu.getRoot(),l.getStart(),l.getEnd()-l.getStart());
                this.VisitTarget(ltmpNode,ltargetnode);
            }
            for (SourceRange r: newsrlist){
                ASTNode rtmpNode =NodeFinder.perform(rcu.getRoot(),r.getStart(),r.getEnd()-r.getStart());
                this.VisitTarget(rtmpNode,rtargetnode);
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
                //       System.out.println("leftdifferencing:"+lcurNode.getEntity());
                //get parameter differnece and binding information

                if(!lcurNode.getValue().equals(rcurNode.getValue())) {
                    // check if the difference is caused by parameters
//                    System.out.println("leftdifferencing:"+lcurNode.getValue()); //md5
//                    VariableTypeBindingTerm vtbtobj = (VariableTypeBindingTerm) lcurNode.getUserObject();
//                    System.out.println("ABSTRACT NAME: " + vtbtobj.getAbstractVariableName());
//                    if (vtbtobj.getAbstractVariableName().startsWith("v")) {
//                        System.out.println("ChildCount: " + lcurNode.getParent().getChildCount());
//                        ArrayList<?> ls = (ArrayList<?>) ((Node)lcurNode.getParent()).getUserObject();
//                        String bindingname = ls.get(0).toString();
//                        if (!patternMap.containsKey(bindingname)) {
//                            patternMap.put(bindingname, new ParameterPattern(bindingname));
//                        }
//                        ParameterPattern pp = (ParameterPattern) patternMap.get(bindingname);
//                        pp.AppendtoISet(lcurNode.getValue());
//                        pp.AppendtoCSet(rcurNode.getValue());
//                    }
//                    System.out.println("rightdiffernt:"+ rcurNode.getValue()); //sha256
//                    VariableTypeBindingTerm vtbt = leftConverter.variableTypeMap.get(lkeymapping.get(lcurNode.getValue()));
//                    System.out.println(vtbt);

                    //traverse the child nodelist, to check if the node is in the parentheses
                    Enumeration<?> children = lcurNode.getParent().children();
                    boolean leftparenthesis = false;
                    List<String> clist = null, ilist = null;
                    String bindingname = "";
                    while (children.hasMoreElements()) {
                        Node node = (Node) children.nextElement();
                        if (node.getValue().equals("(")) {
                            leftparenthesis = true;
                            clist = new ArrayList<String>();
                            ilist = new ArrayList<String>();
                            ArrayList<?> ls = (ArrayList<?>) ((Node)lcurNode.getParent()).getUserObject();
                            bindingname = ls.get(0).toString();
                        }
                        if (node.getValue().equals(")")) {
                            leftparenthesis = false;
                            if (!patternMap.containsKey(bindingname)) {
                                patternMap.put(bindingname, new ParameterPattern(bindingname));
                            }
                            ParameterPattern pp = (ParameterPattern) patternMap.get(bindingname);
                            pp.AppendtoISet(ilist);
                            pp.AppendtoCSet(clist);
                        }
                        // if the node is inside the parenthesis, then the difference is caused by the parameters
                        if (leftparenthesis && node.getValue().equals(lcurNode.getValue())) {
                            clist.add(rcurNode.getValue());
                            ilist.add(lcurNode.getValue());
                        }
                    }
                }

            }
            for(Map.Entry<?,?> entry: this.patternMap.entrySet()) {
                System.out.println("Key: " + entry.getKey());
            }

        }

    /**
     * Deserialize the file
     * @param
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void SavetoFile() throws IOException {
        FileOutputStream fos = new FileOutputStream("pattern.ser");
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(this.patternMap);
        oos.close();
        fos.close();
        System.out.println("Save!");
    }

    /**
     * Deserialize the file
     * @param filename
     * @throws IOException
     * @throws ClassNotFoundException
     */
    public void LoadFromFile(String filename) throws IOException, ClassNotFoundException{
        FileInputStream fis = new FileInputStream(filename);
        ObjectInputStream ois = new ObjectInputStream(fis);
        this.patternMap = (HashMap) ois.readObject();
        for(Map.Entry<?,?> entry: this.patternMap.entrySet()) {
            System.out.println(entry.getKey());
        }
        ois.close();
        fis.close();
    }


}




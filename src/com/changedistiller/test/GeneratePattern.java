package com.changedistiller.test;

import ch.uzh.ifi.seal.changedistiller.ChangeDistiller;
import ch.uzh.ifi.seal.changedistiller.distilling.FileDistiller;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.SourceRange;
import ch.uzh.ifi.seal.changedistiller.model.entities.Delete;
import ch.uzh.ifi.seal.changedistiller.model.entities.Insert;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import ch.uzh.ifi.seal.changedistiller.model.entities.Update;
import com.changedistiller.test.DAO.DBHandler;
import edu.vt.cs.append.FineChangesInMethod;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.NodeFinder;

import java.io.*;
import java.util.*;

public class GeneratePattern {

    private Set<String> methodtype = new HashSet<>();
    private Map<String, CodePattern> patternMap = new HashMap<>();
    private File left;
    private File right;
    private CompilationUnit lcu;
    private CompilationUnit rcu;

    GeneratePattern(){}

    GeneratePattern(File left, File right, CompilationUnit lcu, CompilationUnit rcu){
        this.left = left;
        this.right = right;
        this.lcu = lcu;
        this.rcu= rcu;
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
        List<ASTNode> lNode = new ArrayList<>();
        List<String> lNodeType = new ArrayList<>();
        List<ASTNode> lNodeArgument = new ArrayList<>();
        List<ASTNode> rNode = new ArrayList<>();
        List<String> rNodeType = new ArrayList();
        List<ASTNode> rNodeArgument = new ArrayList<>();
        String matchingExpression = "";
        CustomVisitor customVisitor = new CustomVisitor();
        for (SourceRange l: srlist) {
            ASTNode ltmpNode = NodeFinder.perform(lcu.getRoot(),l.getStart(),l.getEnd()-l.getStart());
            customVisitor.VisitTarget(ltmpNode, lNode, lNodeArgument);
//                System.out.println("test the nodetype: " + customVisitor.bindingName);
            lNodeType.add(customVisitor.bindingName);
            matchingExpression = customVisitor.matchingExpression;
        }
        for (SourceRange r: newsrlist){
            ASTNode rtmpNode = NodeFinder.perform(rcu.getRoot(),r.getStart(),r.getEnd()-r.getStart());
            customVisitor.VisitTarget(rtmpNode, rNode, rNodeArgument);
            rNodeType.add(customVisitor.bindingName);
        }

        //composite
        if (lNode.size() > 1 || rNode.size() > 1) {
            CompositePattern compositePattern = new CompositePattern(lcu, rcu);
            patternMap.put(compositePattern.getName(), compositePattern);
            System.out.println("left:");
            compositePattern.getLcuTemplateStatements().stream().forEach(x -> System.out.println(x));
            System.out.println("right");
            compositePattern.getRcuTemplateStatements().stream().forEach(x -> System.out.println(x));
            return;
        }

        CodePattern name = new NamePattern();
        //compare type, the name are different
        for(int i =0; i<lNodeType.size();i++) {
            if(!(lNodeType.get(i).equals(rNodeType.get(i)))){
                ((NamePattern) name).AppendtoINameSet(lNodeType.toString());
                ((NamePattern) name).AppendtoIClassSet(lNode.toString());

                ((NamePattern) name).AppendtoCNameSet(rNodeType.toString());
                ((NamePattern) name).AppendtoCClassSet(rNode.toString());
            }

        }

        // Compare arguments directly
        for(int i = 0; i < lNodeArgument.size(); i++) {
            CodePattern codePattern;
            String lArg = lNodeArgument.get(i).toString();
            String rArg = rNodeArgument.get(i).toString();
            if (!lArg.equals(rArg)) {
                if (lArg.contains("/")) {
                    codePattern = new ParameterPattern(lNodeType.get(0), i);
                    ((ParameterPattern) codePattern).AppendtoISet(this.divideArgument(lArg, null));
                    ((ParameterPattern) codePattern).AppendtoCSet(this.divideArgument(rArg, null));
                    this.patternMap.put(codePattern.toString(), codePattern);
                } else {
                    if(lArg.matches("\\d+")){//if the argument is number
                        codePattern = new NumberPattern(lNodeType.get(0), i);
                        ((NumberPattern) codePattern).AppendtoISet(lArg);
                        ((NumberPattern) codePattern).AppendtoCSet(rArg);
                        this.patternMap.put(codePattern.toString(), codePattern);
                    }
                    //If the argument does not contain any slash, go with the normal case
                    else {
                        codePattern = new ParameterPattern(lNodeType.get(0), i);
                        ((ParameterPattern) codePattern).AppendtoISet(lArg);
                        ((ParameterPattern) codePattern).AppendtoCSet(rArg);
                        this.patternMap.put(codePattern.toString(), codePattern);
                    }
                }
            }
        }

        DBHandler db = new DBHandler();
        for(Map.Entry<String, CodePattern> entry: this.patternMap.entrySet()) {
            db.WritetoDB(entry.getKey(), entry.getValue(), matchingExpression);
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

    /**
     * For argument like "AES/CBC/NoPadding", it will return {"AES/$/$", "$/CBC/$", "$/$/NoPadding"}
     * @param arg,
     * @return
     */
    public List<String> divideArgument(String arg, Set<Integer> groups) {
        List<String> genericArgs = new ArrayList<>();
        String[] splitArg = arg.split("\\/");
        StringBuilder group_str = new StringBuilder();
        if (splitArg.length == 1)
            genericArgs.add(genericArgs.get(0));
        else {
            for (int i = 0; i < splitArg.length; i++) {
                if (groups.contains(i)) {
                    group_str.append(splitArg[i]);
                    group_str.append("/");
                }
                else {
                    genericArgs.add(splitArg[i]);
                }
            }
        }
        if (group_str.length() > 0) group_str.deleteCharAt(group_str.length()-1);
        genericArgs.add(group_str.toString());
        return genericArgs;
    }

}




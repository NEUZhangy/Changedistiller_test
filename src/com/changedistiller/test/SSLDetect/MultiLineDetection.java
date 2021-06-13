package com.changedistiller.test.SSLDetect;

import com.github.javaparser.JavaToken;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.examples.slice.BackwardResult;
import com.ibm.wala.examples.slice.ProBuilder;
import com.ibm.wala.examples.slice.StartPoints;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.util.CancelException;
import utils.StringSimilarity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MultiLineDetection {

    public String[] args;
    public String methodT; // method type
    public Map<String, String> paramMap = new HashMap<>();
    public List<Object> paramList = new ArrayList<>();
    private static final Logger log = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);
    private ProBuilder proBuilder;
    private String projectSource;
    public Set<String> correctSet;
    private Slicer.DataDependenceOptions dOptions = Slicer.DataDependenceOptions.FULL;
    private Slicer.ControlDependenceOptions cOptions = Slicer.ControlDependenceOptions.FULL;
    private CallGraph completeCG;
    private SDG<InstanceKey> completeSDG;

    public MultiLineDetection(String classPath, String projectSource, Set<String> correctSet)
            throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        proBuilder = new ProBuilder(classPath, dOptions, cOptions);
        completeCG = proBuilder.getTargetCG();
        completeSDG = proBuilder.getCompleteSDG();
        this.projectSource = projectSource;
        this.correctSet = correctSet;
    }

    public void start(String callee, String functionType)
            throws ClassHierarchyException, CancelException, IOException {
        StartPoints startPoints = new StartPoints(completeCG, callee, functionType);
        Set<Statement> allStartStmt = startPoints.getStartStmts();
        for (Statement stmt : allStartStmt) {
            String filePath = this.projectSource;
            String className = stmt.getNode().getMethod().getDeclaringClass().getName().toString();
            filePath = filePath.concat(className.substring(1));
            filePath = filePath.replace('/', '\\');
            filePath = filePath.concat(".java");
            System.out.println(filePath);
            System.out.println(
                    "-----------------------------------"
                            + className
                            + "----------------------------------------------");
            List<Statement> stmtList = new ArrayList<>();
            stmtList.add(stmt);
            List<Integer> lineNum = getStmtLineNum(stmtList);
            List<String> matchingStmt = getmatchStmts(lineNum, filePath);
            List<String> correctStmt = getCorrectstmts();
            getMissingStmts(correctStmt, matchingStmt, lineNum);
        }
    }

    private List<Integer> getStmtLineNum(Collection<Statement> slice) {
        List<Integer> lineNum = new ArrayList<>();
        for (Statement stmt : slice) {
            if (stmt.getNode()
                                    .getMethod()
                                    .getDeclaringClass()
                                    .getClassLoader()
                                    .getName()
                                    .toString()
                                    .compareToIgnoreCase("primordial") == 0
                    || stmt.getKind() != Statement.Kind.NORMAL) continue;
            lineNum.add(getLineNumber(stmt));
            log.log(Level.INFO, String.valueOf(getLineNumber(stmt)));
        }
        return lineNum;
    }

    private List<String> getCorrectstmts() {
        List<String> correctStmts = new ArrayList<>(this.correctSet);
        return correctStmts;
    }

    private List<String> getmatchStmts(List<Integer> lineNum, String filePath)
            throws FileNotFoundException {
        // The following part is to get the matching statements from the source code:
        // Use javaparser
        // 1. visit the file to get all the variables and save to the varList
        // 2. normalize the statements by replacing the variables but leave the constants.
        CompilationUnit cu = StaticJavaParser.parse(new File(filePath));
        Map<String, String> varList = new HashMap<>();
        VoidVisitor<Map<String, String>> visitor = new VariableDeclarationVisitor();
        cu.accept(visitor, varList);
        for (Map.Entry<String, String> entry : varList.entrySet()) {
            paramMap.put("\\" + entry.getValue(), entry.getKey());
        }
        int prev = -1;
        List<String> matchingStmt = new ArrayList<>();
        String str = "";
        for (JavaToken tr : cu.getTokenRange().get()) {
            int nline = tr.getRange().get().begin.line;
            if (lineNum.contains(nline)) {
                if (prev == -1) prev = nline;
                if (prev != nline) {
                    matchingStmt.add(str.trim());
                    str = "";
                    prev = nline;
                }
                if (varList.containsKey(tr.getText())) {
                    str += varList.get(tr.getText());
                } else {
                    str += tr.getText();
                }
            }
        }
        matchingStmt.add(str.trim());
        return matchingStmt;
    }

    private String getVar(String s) {
        String pattern = "(.*)(\\$v_\\d+)(.*)";
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(s);
        if (m.find()) {
            return m.group(2);
        } else return "";
    }

    public int getLineNumber(Statement stmt) {
        int bcIndex, instructionIndex = ((NormalStatement) stmt).getInstructionIndex();
        try {
            bcIndex =
                    ((ShrikeBTMethod) stmt.getNode().getMethod())
                            .getBytecodeIndex(instructionIndex);
            try {
                int src_line_number = stmt.getNode().getMethod().getLineNumber(bcIndex);
                return src_line_number;
            } catch (Exception e) {
                System.err.println("Bytecode index no good");
                System.err.println(e.getMessage());
            }
        } catch (Exception e) {
            System.err.println("it's probably not a BT method (e.g. it's a fakeroot method)");
            System.err.println(e.getMessage());
        }
        return -1;
    }

    private void getMissingStmts(
            List<String> correctstmts, List<String> matchingStmt, List<Integer> lineNum) {
        StringSimilarity ss = new StringSimilarity();
        // get the matching ratio
        Set<Integer> visited = new HashSet<>();
        // get the matching line
        HashMap<Integer, Integer> addLines = new HashMap<>();
        HashMap<String, String> varMap = new HashMap<>();
        for (int i = 0; i < matchingStmt.size(); i++) {
            for (int j = 0; j < correctstmts.size(); j++) {
                if (visited.contains(j)) continue;
                double result = ss.calculateSimilarity(matchingStmt.get(i), correctstmts.get(j));
                if (result >= 0.6) {
                    visited.add(j);
                    String oriVar = getVar(matchingStmt.get(i));
                    String descVar = getVar(correctstmts.get(j));
                    varMap.put(oriVar, '\\' + descVar);
                    addLines.put(j, lineNum.get(i));
                }
            }
        }
        // get insert after linenum
        int pivot = -1;
        for (int j = 0; j < correctstmts.size(); j++) {
            if (visited.contains(j)) {
                for (int i = 0; i < j && pivot == -1; i++) {
                    String stmtwithvar = replaceVar(correctstmts.get(i), varMap);
                    System.out.println(
                            String.format(
                                    "Add Statement before Line %d: %s",
                                    addLines.get(j), stmtwithvar));
                }
                pivot = addLines.get(j);
                continue;
            }
            if (pivot == -1) continue;
            String stmtwithvar = replaceVar(correctstmts.get(j), varMap);
            System.out.println(
                    String.format("Add Statement after Line %d: %s", pivot, stmtwithvar));
        }
    }

    private String replaceVar(String stmtwithvar, HashMap<String, String> varMap) {
        String s = null;
        for (Map.Entry<String, String> entry : paramMap.entrySet()) {
            if (varMap.containsKey(entry.getKey().substring(1))) {
                s =
                        stmtwithvar.replaceAll(
                                varMap.get(entry.getKey().substring(1)), entry.getValue());
            }
        }
        return s;
    }
}

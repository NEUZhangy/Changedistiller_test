package com.ibm.wala.examples.slice;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.ssa.*;
import com.ibm.wala.util.CancelException;

import java.io.IOException;
import java.util.*;

public class InterRetrive {

    private CallGraph completeCG;
    private Set<Statement> allStartStmt = new HashSet<>();
    private SDG<InstanceKey> completeSDG;
    private HashMap<Integer, List<Object>> paramValue = new HashMap<>();
    private Map<String, Map<Integer, List<Object>>> classVarMap = new HashMap<>();
    public ISupergraph<Statement, PDG<? extends InstanceKey>> backwardSuperGraph;
    private ProBuilder proBuilder;
    private Slicer.DataDependenceOptions dOptions = Slicer.DataDependenceOptions.FULL;
    private Slicer.ControlDependenceOptions cOptions = Slicer.ControlDependenceOptions.FULL;

    public InterRetrive(String classPath) throws ClassHierarchyException, CallGraphBuilderCancelException, IOException {
        proBuilder = new ProBuilder(classPath, dOptions, cOptions);
        completeCG = proBuilder.getTargetCG();
        completeSDG = proBuilder.getCompleteSDG();
        backwardSuperGraph = proBuilder.getBackwardSuperGraph();
    }

    private void init() {
        paramValue.clear();
        classVarMap.clear();
    }

    public void start(String classpath, String callee, String functionType) throws ClassHierarchyException, CancelException, IOException {
        init();
        StartPoints startPoints = new StartPoints(completeCG, callee, functionType);
        allStartStmt = startPoints.getStartStmts();
        for(Statement stmt: allStartStmt){
            String className = stmt.getNode().getMethod().getDeclaringClass().getName().toString();
            if (className.compareTo("Lorg/cryptoapi/bench/staticsalts/StaticSaltsABICase3") != 0)
                continue;
            System.out.println("-----------------------------------"+className+"----------------------------------------------");
            BackwardResult backwardResult = new BackwardResult(completeCG,proBuilder.getBuilder().getPointerAnalysis(),completeSDG,dOptions,cOptions);
            backwardResult.setReachingStmts(stmt);
            List<Statement> stmtList = backwardResult.getFilterReaStmts();
            setParamValue(stmt, stmtList);
            for (int i = 0; i < paramValue.size(); i++) {
                System.out.println("target parameter is : " + paramValue.get(i));
            }
            classVarMap.put(className, paramValue);
            System.out.println("---------------------------------done analysis---------------------------------------");
        }

    }


    public void setParamValue(Statement targetStmt, List<Statement> stmtList){
        SSAInstruction targetInst = ((StatementWithInstructionIndex) targetStmt).getInstruction();
        Set<Integer> uses = new HashSet<>();
        CGNode targetNode = targetStmt.getNode();
        IR targetIR = targetNode.getIR();
        SymbolTable st = targetIR.getSymbolTable();
        DefUse du = targetNode.getDU();
        Set<Integer> visited = new HashSet<>();
        Set<Statement> visitedStmt = new HashSet<>();

        Statement newRStmt = targetStmt;
        if (targetInst instanceof SSAInvokeInstruction) {
            int i = ((SSAInvokeInstruction) targetInst).isStatic() == true ? 0 : 1;
            int neg = ((SSAInvokeInstruction) targetInst).isStatic() == true ? 0 : -1;
            int numOfUse = targetInst.getNumberOfUses();
            //get all parameter, by process one by one
            while (i < numOfUse) {
                targetStmt = newRStmt;
                visitedStmt  .clear();
                List<Object> ans = new ArrayList<>(); //have more possible value;
                int use = targetInst.getUse(i);
                boolean result = true;
                while (result){
                    IntraRetrive intraRetrive= new IntraRetrive(targetStmt, stmtList,backwardSuperGraph, paramValue, use);
                    IntraResult intraResult = intraRetrive.setParamValue(targetStmt, i+ neg, ans);
                    if(!intraResult.result){
                        uses =  intraResult.getUses(); /*mayhave more than one use to trace*/
                        for(Integer u: uses){
                            EdgeProce edgeProce = new EdgeProce(backwardSuperGraph, intraResult.resultMap, u,stmtList,paramValue,completeCG);
                            edgeProce.visited.addAll(visitedStmt);
                            IntraResult edgeResult = edgeProce.edgeSolver(u,i+neg,ans);
                            /*add here to prevent the  infinite loop*/
                            edgeResult.setVisitedStmt(edgeProce.visited);
                            visitedStmt.addAll(edgeResult.visitedStmt);

                            if(edgeResult.resultMap.size()!=0 ) {
                                targetStmt = edgeProce.targetStmt;
                                use = edgeProce.use;
                                result = true;
                                setParamValue(targetStmt, use, result, i+neg, visitedStmt, stmtList,ans);
                                continue;
                            }
                            else continue;
                        }
                        result = false;
                    }
                    else result = false;
                }
                i++;
            }

            if (paramValue.size() == numOfUse){
                System.out.println("finish");
                return;
            }
        }
    }

    public void setParamValue(Statement targetStmt, int use, boolean result, int pos, Set<Statement> visitedStmt, List<Statement> stmtList,List<Object> ans){

        if(result){
            IntraRetrive intraRetrive= new IntraRetrive(targetStmt, stmtList,backwardSuperGraph, paramValue, use);
            IntraResult intraResult = intraRetrive.setParamValue(targetStmt, pos, ans);
            if(!intraResult.result){
                Set<Integer> uses =  intraResult.getUses(); /*mayhave more than one use to trace*/
                for(Integer u: uses){
                    EdgeProce edgeProce = new EdgeProce(backwardSuperGraph, intraResult.resultMap, u,stmtList,paramValue,completeCG);
                    edgeProce.visited.addAll(visitedStmt);
                    IntraResult edgeResult = edgeProce.edgeSolver(u,pos,ans);
                    /*add here to prevent the  infinite loop*/
                    edgeResult.setVisitedStmt(edgeProce.visited);
                    visitedStmt.addAll(edgeResult.visitedStmt);
                    if(edgeResult.resultMap .size()!= 0 ) {
                        targetStmt = edgeProce.targetStmt;
                        use = edgeProce.use;
                        result = true;
                        setParamValue(targetStmt, use,result, pos, visitedStmt,stmtList,ans);
                        break;
                    }
                    else continue;
                }
            }
            else result = false;
        }
        return;
    }

    public HashMap<Integer, List<Object>> getParamValue() {
        return paramValue;
    }

    public Map<String, Map<Integer, List<Object>>> getClassVarMap() {
        return classVarMap;
    }
}

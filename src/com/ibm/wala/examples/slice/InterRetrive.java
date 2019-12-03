package com.ibm.wala.examples.slice;

import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
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
    public ISupergraph<Statement, PDG<? extends InstanceKey>> backwardSuperGraph;

    public void start(String classpath,String callee,String functionType) throws ClassHierarchyException, CancelException, IOException {
        Slicer.DataDependenceOptions dOptions = Slicer.DataDependenceOptions.FULL;
        Slicer.ControlDependenceOptions cOptions = Slicer.ControlDependenceOptions.FULL;
        ProBuilder proBuilder = new ProBuilder(classpath,dOptions, cOptions);
        completeCG = proBuilder.getTargetCG();
        completeSDG = proBuilder.getCompleteSDG();
        backwardSuperGraph = proBuilder.getBackwardSuperGraph();
        StartPoints startPoints = new StartPoints(completeCG, callee, functionType);
        allStartStmt = startPoints.getStartStmts();
        for(Statement stmt: allStartStmt){

            String className = stmt.getNode().getMethod().getDeclaringClass().getName().toString();
            if (className.compareTo("Lorg/cryptoapi/bench/predictablecryptographickey/PredictableCryptographicKeyABICase2") != 0)
                continue;

            BackwardResult backwardResult = new BackwardResult(completeCG,proBuilder.getBuilder().getPointerAnalysis(),completeSDG,dOptions,cOptions);
            backwardResult.setReachingStmts(stmt);
            List<Statement> stmtList = backwardResult.getFilterReaStmts();
            setParamValue(stmt, stmtList);
            for (int i = 0; i < paramValue.size(); i++) {
                System.out.println("target parameter is : " + paramValue.get(i));

            }
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

        if (targetInst instanceof SSAInvokeInstruction) {
            int i = ((SSAInvokeInstruction) targetInst).isStatic() == true ? 0 : 1;
            int neg = ((SSAInvokeInstruction) targetInst).isStatic() == true ? 0 : -1;
            int numOfUse = targetInst.getNumberOfUses();
            //get all parameter, by process one by one
            while (i < numOfUse) {
                List<Object> ans = new ArrayList<>(); //have more possible value;
                int use = targetInst.getUse(i);
                boolean result = true;
                while (result){
                    IntraRetrive intraRetrive= new IntraRetrive(targetStmt, stmtList,backwardSuperGraph, paramValue, use);
                    IntraResult intraResult = intraRetrive.setParamValue(targetStmt, i+ neg, ans);
                    if(intraResult.result ==false){
                        uses =  intraResult.getUses();
                        for(Integer u: uses){
                            EdgeProce edgeProce = new EdgeProce(backwardSuperGraph, intraResult.resultMap, u,stmtList,paramValue,completeCG);
                            IntraResult edgeResult = edgeProce.edgeSolver(u,i+neg,ans);
                            if(edgeResult.resultMap !=null ) {
                                targetStmt = edgeProce.targetStmt;
                                use = edgeProce.use;
                                break;
                            }
                            else continue;
                        }
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

}

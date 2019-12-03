package com.ibm.wala.examples.slice;

import com.ibm.wala.classLoader.IField;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.propagation.InstanceFieldKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.callgraph.propagation.StaticFieldKey;
import com.ibm.wala.ipa.slicer.*;

import java.util.*;

public class EdgeProce {

    public HashMap<Integer, Statement> resultMap = new HashMap<>();
    ISupergraph<Statement, PDG<? extends InstanceKey>> backwardSuperGraph;
    Integer use = null;
    Set<Integer> uses = new HashSet<>();
    private List<Statement> reachingStmts = new ArrayList<>();
    Statement targetStmt = null;
    private HashMap<Integer, List<Object>> paramValue = new HashMap<>();
    private HashMap<Integer, Statement> statementWithIndex = new HashMap<>();
    private CallGraph completeCG;
    private HashMap<Integer,Statement> newReMap = new HashMap<>();

    public EdgeProce(ISupergraph<Statement, PDG<? extends InstanceKey>> backwardSuperGraph, HashMap<Integer, Statement> resultMap, Integer use
    ,List<Statement> reachingStmts, HashMap<Integer, List<Object>> paramValue, CallGraph cg){
        this.resultMap = resultMap;
        this.backwardSuperGraph = backwardSuperGraph;
        this.use = use;
        this.reachingStmts = reachingStmts;
        this.paramValue = paramValue;
        this.completeCG = cg;
    }

    public IntraResult edgeSolver(int use, int pos, List<Object> ans){
        this.targetStmt = getCurStmt();
        if(targetStmt != null){
            Set<Statement> visited = new HashSet<>();
           Boolean result = checkEdges(pos, ans, targetStmt,use, completeCG, visited);
           IntraResult intraResult = new IntraResult(targetStmt,paramValue, uses, result,targetStmt.getNode().getIR(), newReMap);
           return  intraResult;

        }else{
            System.out.println("no matching stmt; check the resultmap in intraRetrive");
        }
        return null;
    }

    public boolean checkEdges(int pos, List<Object> ans, Statement stmt, int use, CallGraph completeCG, Set<Statement> visited){
        boolean result = true;
        //if(visited.contains(stmt)) return false;
        Iterator<Statement> edges = backwardSuperGraph.getSuccNodes(stmt);
        while (edges.hasNext()){
            Statement curEdge = edges.next();
            if(visited.contains(curEdge)) break;
            visited.add(curEdge);
            switch (curEdge.getKind()) {
                case HEAP_PARAM_CALLEE:
                    HeapStatement.HeapParamCallee heapcallee = (HeapStatement.HeapParamCallee) curEdge;
                    PointerKey loc = heapcallee.getLocation();
                    IField iField = null;
                    if(loc instanceof StaticFieldKey){
                        StaticFieldKey fieldLoc = (StaticFieldKey) loc;
                        iField = fieldLoc.getField();
                    }
                    if(loc instanceof InstanceFieldKey){
                        InstanceFieldKey fieldLoc = (InstanceFieldKey) loc;
                        iField = fieldLoc.getField();
                    }
                    System.out.println("--------------checkfiled within class first--------" + iField.getName().toString());
                    Collection<IField> fields = curEdge.getNode().getMethod().getDeclaringClass().getDeclaredInstanceFields();
                    if (fields.contains(iField)) { // search the init function within current class
                        InitRetrive initRetrive = new InitRetrive(curEdge, iField, loc,paramValue,backwardSuperGraph);
                        Set<Integer> uses = new HashSet<>();
                        uses.add(use);
                        Set<Integer> visiteduse = new HashSet<>();
                        if(initRetrive.setValues(completeCG, pos,ans,uses, visiteduse)){
                            continue;
                        }
                        else{
                            checkEdges(pos, ans, curEdge,use, completeCG,visited);
                            break;
                        }
                    }
                    else{
                        checkEdges(pos, ans, curEdge,use, completeCG,visited);
                        break;
                    }


                case HEAP_PARAM_CALLER:
                    checkEdges(pos, ans, curEdge,use, completeCG,visited);
                    break;
                case HEAP_RET_CALLER:
                    checkEdges(pos, ans, curEdge, use, completeCG,visited);
                    break;
                case HEAP_RET_CALLEE:
                    checkEdges(pos, ans, curEdge,use,completeCG,visited);
                    break;

                case PARAM_CALLEE:
                    ParamCallee paramCallee =(ParamCallee) curEdge;
                    int valNum = paramCallee.getValueNumber();
                    if(valNum == use){
                        checkEdges(pos, ans,curEdge,use,completeCG,visited);
                        continue;
                    }
                    else continue;
                case NORMAL_RET_CALLER:
                    checkEdges(pos, ans, curEdge,use, completeCG,visited);
                    break;
                case NORMAL_RET_CALLEE:
                    checkEdges(pos, ans, curEdge,use, completeCG,visited);
                    break;

                case PARAM_CALLER:
                    ParamCaller paraCaller = (ParamCaller) curEdge;
                    int newUse = paraCaller.getValueNumber();
                    checkEdges(pos, ans, curEdge, newUse, completeCG,visited);
                    break;

                case METHOD_ENTRY:
                    break;

                case NORMAL:
                    this.targetStmt = curEdge;
                    this.use = use;
                    result = false;
                    uses.add(use);
                    newReMap.put(use, targetStmt);
                    return result;
            }
//            if(curEdge.getKind() == HEAP_PARAM_CALLEE){
//                HeapStatement.HeapParamCallee heapcallee = (HeapStatement.HeapParamCallee) curEdge;
//                PointerKey loc = heapcallee.getLocation();
//                IField iField = null;
//                if(loc instanceof StaticFieldKey){
//                    StaticFieldKey fieldLoc = (StaticFieldKey) loc;
//                    iField = fieldLoc.getField();
//                }
//                if(loc instanceof InstanceFieldKey){
//                    InstanceFieldKey fieldLoc = (InstanceFieldKey) loc;
//                    iField = fieldLoc.getField();
//                }
//                System.out.println("--------------checkfiled within class first--------" + iField.getName().toString());
//                Collection<IField> fields = curEdge.getNode().getMethod().getDeclaringClass().getDeclaredInstanceFields();
//                if (fields.contains(iField)) { // search the init function within current class
//                    InitRetrive initRetrive = new InitRetrive(curEdge, iField, loc,paramValue,backwardSuperGraph);
//                    Set<Integer> uses = new HashSet<>();
//                    uses.add(use);
//                    Set<Integer> visited = new HashSet<>();
//                    if(initRetrive.setValues(completeCG, pos,ans,uses, visited)){
//                        continue;
//                    }
//                }
//
//            }
//
//            if(curEdge.getKind() == HEAP_PARAM_CALLER){
//                if(checkEdges(pos, ans, curEdge,use, completeCG))
//                continue;
//            }
//
//            if(curEdge.getKind() == HEAP_RET_CALLER){
//                checkEdges(pos, ans, curEdge, use, completeCG);
//                continue;
//            }
//
//            if(curEdge.getKind() ==  PARAM_CALLEE){
//                ParamCallee paramCallee =(ParamCallee) curEdge;
//                int valNum = paramCallee.getValueNumber();
//                if(valNum == use){
//                    checkEdges(pos, ans,curEdge,use,completeCG);
//                    continue;
//                }
//                else continue;
//            }
//
//            if(curEdge .getKind() == NORMAL_RET_CALLEE){
//                checkEdges(pos, ans, curEdge,use, completeCG);
//                continue;
//            }
//            if(curEdge.getKind() == NORMAL_RET_CALLER){
//                checkEdges(pos, ans, curEdge, use, completeCG);
//                continue;
//            }
//
//            if(curEdge.getKind() == PARAM_CALLER){
//                ParamCaller paraCaller = (ParamCaller) curEdge;
//                int newUse = paraCaller.getValueNumber();
//                checkEdges(pos, ans, curEdge, newUse, completeCG);
//                continue;
//            }
//
//            if(curEdge.getKind() == NORMAL){
//                this.targetStmt = stmt;
//                this.use = use;
//
//
        }
        if(resultMap.isEmpty()){
            System.out.println("No succNode for curedge: " + stmt);
        }
        return  result;
    }

    public Statement getCurStmt(){
        if(resultMap.get(use)!= null)
            return resultMap.get(use);
        else
            return null;
    }


}

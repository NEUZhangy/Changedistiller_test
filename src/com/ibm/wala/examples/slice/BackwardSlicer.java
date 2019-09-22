package com.ibm.wala.examples.slice;

import com.ibm.wala.classLoader.Language;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.examples.ExampleUtil;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.ssa.*;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.intset.BitVectorIntSet;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;

import javax.swing.plaf.nimbus.State;
import java.io.IOException;
import java.util.*;

public class BackwardSlicer {

    private List<Object> ParamValue = new ArrayList<>();
    private List<Statement> stmtList = new ArrayList<>();// save the filter slice result
    private Set<String> fieldName = new HashSet<>();
    private Map<String, Object> varMap = new HashMap<>();
    /* to handle the different behavior WALA backward slicing, when only one block in the slicing result,
    the slicing list is reversed. When multi function is in the list, the order is not reversed.
    */
    private Boolean blockIsReverse = false;

    public List<Object> getParamValue() {
        return ParamValue;
    }

    public void run(String path,
                    String mainClass,
                    String callee,
                    String caller,
                    String functionType
    ) throws IOException, ClassHierarchyException, CancelException {
        Slicer.DataDependenceOptions dataDependenceOptions = Slicer.DataDependenceOptions.NO_BASE_NO_HEAP_NO_EXCEPTIONS;
        Slicer.ControlDependenceOptions controlDependenceOptions = Slicer.ControlDependenceOptions.FULL;
        AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(path, null);
        ExampleUtil.addDefaultExclusions(scope);
        ClassHierarchy cha = ClassHierarchyFactory.make(scope);
        Iterable<Entrypoint> entrypoints = com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(scope, cha,
                mainClass);
        AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);
        CallGraphBuilder<InstanceKey> builder = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options,
                new AnalysisCacheImpl(), cha, scope);
        CallGraph cg = builder.makeCallGraph(options, null);
        SDG<InstanceKey> sdg = new SDG<>(cg, builder.getPointerAnalysis(),
                dataDependenceOptions, controlDependenceOptions);
        Statement targetStmt = null;

        for (CGNode node: cg) {
            Statement stmt = findCallTo(node, callee, functionType);
            if (stmt != null) {
                targetStmt = stmt;
                break;
            }
        }

        Collection<Statement> relatedStmts = Slicer.computeBackwardSlice(targetStmt, cg, builder.getPointerAnalysis(),
                dataDependenceOptions, controlDependenceOptions);

        // Filter all non application stmts
        filterStatement(relatedStmts);
        setParamValue(targetStmt);

        // Cannot use targetStmt.getNode().getMethod(). It is not equal to the original statement
        // Use SSAInstruction instead
        StatementWithInstructionIndex stmtwithindex = (StatementWithInstructionIndex) targetStmt;
        SSAInstruction inst = stmtwithindex.getInstruction();

        int neg = 0;
        for (int i = 0; i < inst.getNumberOfUses(); i++) {
            if (inst instanceof SSAInvokeInstruction && !((SSAInvokeInstruction)inst).isStatic() && i == 0) {
                neg = -1;
                continue;
            }
            getParameter(i+neg);
        }
    }

    /**
     This function is to get the parameter value.
     It will contains five situations:
     TODO:
     1. the use-index is already in the symboltable.
     2. Intra-procedure case/ array ref (double check if the array included in this case)
     3. Inter-procedure case.
     4. Static / class field case.
     5. MultiClass Case.
     set the value into ParaValue list
     */
    public void getParameter(int i) {
        System.out.println("This is the " + i + "th parameter for the target function: " + ParamValue.get(i));
    }

    public void setParamValue(Statement targetStmt){
        SSAInstruction targetInst = ((StatementWithInstructionIndex)targetStmt).getInstruction();
        Set<Integer> uses = new HashSet<>();
        IR targetIR = targetStmt.getNode().getIR();
        SymbolTable st = targetIR.getSymbolTable();
        if(targetInst instanceof SSAInvokeInstruction){
            int i = ((SSAInvokeInstruction)targetInst).isStatic() == true? 0 : 1;
            int numOfUse = targetInst.getNumberOfUses();
            while(i < numOfUse){
                int use = targetInst.getUse(i);
                if(st.isConstant(use)){
                    ParamValue.add(st.getConstantValue(use));
                }
                else{
                    uses.add(use); // can't get the parameter within one block;
                    /*
                     * If I cannot get the value on that statement directly, which means that the value is not
                     * in the symboltable, then run the following analysis:
                     *   1. separate all the statements by their functions/blocks
                     *   2. reversely loop all functions/blocks. reverse means from the block of targetstmt -> main
                     *   3. when handling the specific function, pass the target use value number list to each function
                     */
                    String selector = null;
                    List<Statement> stmtInBlock = new ArrayList<>();
                    for (Statement stmt: stmtList) {
                        String func = stmt.getNode().getMethod().getDeclaringClass().getName().toString() + " " +
                                stmt.getNode().getMethod().getSelector().getName().toString();
                        if (selector == null) {
                            selector = func;
                            stmtInBlock.add(stmt);
                        }
                        else if (selector.compareToIgnoreCase(func) == 0){
                            //System.out.println(stmt);
                            stmtInBlock.add(stmt);
                        }
                        else {
                            blockIsReverse = true;
                            setParamValue(targetStmt, uses, stmtInBlock);
                            stmtInBlock.clear();
                            selector = func;
                        }
                    }
                    setParamValue(targetStmt, uses, stmtInBlock);
                }
                i++;
                blockIsReverse = false;
            }
            if(ParamValue.size() == numOfUse)
                return;
        }
    }


    //TODO: this function should be refactor as follows:
    //   1. if slice statement is within one single block (no passin. ) - done
    //   2. cross the block (pass in, ssaput_)
    //   3. for pass in param, use negative number of mark the position of varables.
    public void setParamValue(Statement targetStmt, Set<Integer> uses,
                              List<Statement> stmtInBlock) {
        int calleeCount = 0, callerCount = 0;
        if (!blockIsReverse) {
            Collections.reverse(stmtInBlock);
        }
        Set<SSAInstruction> definsts = new HashSet<>();
        Set<Integer> visited = new HashSet<>();
        for (int i = 0; i < stmtInBlock.size(); i++) {
            Statement stm = stmtInBlock.get(i);
            if (stm.toString().equals(targetStmt.toString())) continue;
            if (stm.getKind() == Statement.Kind.PARAM_CALLER) {
                if (uses.contains(-callerCount)) {
                    uses.remove(-callerCount);
                    ParamCaller paramCaller = (ParamCaller) stm;
                    SSAInstruction inst = paramCaller.getInstruction();
                    int use = paramCaller.getValueNumber();
                    SymbolTable st = paramCaller.getNode().getIR().getSymbolTable();
                    if (uses.size() == 0 && !visited.contains(use)) {
                        if (st.isConstant(use)) {
                            this.ParamValue.add(st.getConstantValue(use));
                            visited.add(use);
                        }
                        else {
                            uses.add(use);
                        }
                    }
                }
                callerCount ++;
                continue;
            }
            if (stm.getKind() == Statement.Kind.PARAM_CALLEE) {
                ParamCallee paramCallee = (ParamCallee) stm;
                int use = paramCallee.getValueNumber();
                if (uses.contains(use)) {
                    uses.remove(use);
                    uses.add(-calleeCount);
                    calleeCount++;
                }
                continue;
            }
            if (!(stm instanceof StatementWithInstructionIndex)) continue;
            SSAInstruction inst = ((StatementWithInstructionIndex) stm).getInstruction();
            IR ir = stm.getNode().getIR();
            DefUse du = stm.getNode().getDU();
            SymbolTable st = ir.getSymbolTable();

            for (int j = 0; j < inst.getNumberOfDefs(); j++) {
                uses.remove(inst.getDef(j));
            }

            for (int j = 0; j < inst.getNumberOfUses(); j++) {
                int use = inst.getUse(j);
                if (j == 0 && ((inst instanceof SSAInvokeInstruction
                        && !((SSAInvokeInstruction) inst).isStatic()) || !(inst instanceof SSAAbstractInvokeInstruction))
                        && !st.isConstant(use))
                    continue;
                if (!st.isConstant(use)) {
                    uses.add(use);
                    if (du.getDef(use) != null) definsts.add(du.getDef(use));
                } else {
                    //System.out.println("\t" + use + " " + st.getConstantValue(use));
                    if (uses.size() == 0 && !visited.contains(use)) {
                        this.ParamValue.add(st.getConstantValue(use));
                        visited.add(use);
                    }
                }
            }
        }

    }

    // here is the interface for filter out the unrelated statement
    public void filterStatement(Collection<Statement> relatedStmts){
        for (Statement stmt: relatedStmts) {
            if (!stmt.getNode().getMethod().getDeclaringClass().getClassLoader().getName().toString().equals("Primordial")) {
                stmtList.add(stmt);
            }
        }
    }

    /* find the target method, should be a method invocation*/
    public Statement findCallTo(CGNode n, String methodName, String methodType) {
        IR ir = n.getIR();
        if (ir == null) return null;

        for (SSAInstruction s : Iterator2Iterable.make(ir.iterateAllInstructions())) {
            if (s instanceof SSAInvokeInstruction) {
                SSAInvokeInstruction call = (SSAInvokeInstruction) s;
                // Get the information binding
                String methodT = call.getCallSite().getDeclaredTarget().getSignature();
                if (call.getCallSite().getDeclaredTarget().getName().toString().equals(methodName)
                        && methodT.contains(methodType)) {
                    // 一个例子
                    //if (call.getCallSite().getDeclaredTarget().getSignature().contains("Cipher")) continue;
                    IntSet indices = ir.getCallInstructionIndices(((SSAInvokeInstruction) s).getCallSite());
                    Assertions.productionAssertion(indices.size() == 1, "expected 1 but got " + indices.size());
                    return new NormalStatement(n, indices.intIterator().next());
                }
            }
        }
        //Assertions.UNREACHABLE("failed to find call to " + methodName + " in " + n);
        return null;
    }


}

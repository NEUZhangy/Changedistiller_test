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

import java.io.IOException;
import java.util.*;

public class BackwardSlicer {

    private List<Object> ParamValue = new ArrayList<>();
    private List<Statement> stmtList = new ArrayList<>();// save the filter slice result
    private Set<String> fieldName = new HashSet<>();
    private Map<String, Object> varMap = new HashMap<>();


    public List<Object> getParamValue() {
        return ParamValue;
    }

    public void run(String path,
                    String mainClass,
                    String callee,
                    String caller,
                    String functionType
    ) throws IOException, ClassHierarchyException, CancelException {
        Slicer.DataDependenceOptions dataDependenceOptions = Slicer.DataDependenceOptions.FULL;
        Slicer.ControlDependenceOptions controlDependenceOptions = Slicer.ControlDependenceOptions.NONE;
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
        for (int i = 0; i < inst.getNumberOfUses(); i++) {
            if (inst instanceof SSAInvokeInstruction && !((SSAInvokeInstruction)inst).isStatic() && i == 0) continue;
            getParameter(i-1);
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
        BitVectorIntSet uses = new BitVectorIntSet();
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
                    setParamValue(targetStmt, uses);
                }
                i++;
            }
            if(ParamValue.size() == numOfUse)
                return;
        }
    }

    public void setParamValue(Statement targetStmt, BitVectorIntSet uses) {
        Set<SSAInstruction> definsts = new HashSet<>();
        Map<Integer, Integer> paraMap = new HashMap<>();
        for (int i = stmtList.size() - 1; i >= 0; i--) {
            Statement stm = stmtList.get(i);
            if (stm.toString().equals(targetStmt.toString())) continue;
            if (stm.getKind() == Statement.Kind.PARAM_CALLEE) {
                IR ir = stm.getNode().getIR();
                ParamCallee pacallee = (ParamCallee) stm;
                int var = pacallee.getValueNumber();
                int size = ir.getNumberOfParameters();
                for (int j = 0; j < size; j++) {
                    if (ir.getParameter(j) == var) {
                        paraMap.put(var, j + 1);    // the location of the arguments
                        uses.add(var);
                    }
                }
            } else if (stm.getKind() == Statement.Kind.PARAM_CALLER) {
                IR ir = stm.getNode().getIR();
                SymbolTable st = ir.getSymbolTable();
                ParamCaller pacaller = (ParamCaller) stm;
                SSAInstruction caller = pacaller.getInstruction();
                if (paraMap.size() == 0) continue;// 目前不知道有什么用，没有use没有参数，没有对应的callee 那 只可能是access static filed?
                IntIterator it = uses.intIterator();
                List<Integer> found = new ArrayList<>();
                while (it.hasNext()) {
                    if (paraMap.size() == 0) break;
                    int n = it.next();
                    if (caller.getNumberOfUses() == 0) continue;
                    if (!paraMap.containsKey(n)) {
                        continue;
                    }
                    int varuse = caller instanceof SSAAbstractInvokeInstruction ? paraMap.get(n) - 1 : paraMap.get(n);
                    if (varuse < 0) {
                        paraMap.remove(n);
                        continue;
                    }
                    int use = caller.getUse(varuse);
                    found.add(n);
                    if (st.isConstant(use)) {
                        ParamValue.add(st.getConstantValue(use));
                        System.out.println("Parameter" + st.getConstantValue(use));
                        break;
                    } else {
                        //TODO: Add a logic to handle if the value cannot find here. HERE have big Problem
                        uses.add(use); // not constant, should be passin parameter again, add to use
                        definsts.add(stm.getNode().getDU().getDef(use));
                        paraMap.remove(n); // the value number would changed, remove the previous one. until it meet the caller again and save to paraMap new value
                    }
                }
            } else {
                if (!(stm instanceof StatementWithInstructionIndex)) continue;
                SSAInstruction inst = ((StatementWithInstructionIndex) stm).getInstruction();

                if(inst instanceof SSAArrayStoreInstruction){ //arraystore is another case
                    SSAArrayStoreInstruction arraystore = (SSAArrayStoreInstruction) inst;
                    if(uses.contains(arraystore.getArrayRef())){
                        definsts.add(inst);
                    }
                }

                if(stm.equals(targetStmt) || definsts.contains(inst)
                        || (inst instanceof SSAPutInstruction && fieldName.contains(((SSAPutInstruction)inst).getDeclaredField().getName().toString()))) { //直接依赖DU拿deinsts 有局限性，arraystore不能处理，另外static field Du是空
                    IR ir = stm.getNode().getIR();
                    DefUse du = stm.getNode().getDU();
                    SymbolTable st = ir.getSymbolTable();
                    if(inst instanceof SSAGetInstruction){
                        SSAGetInstruction getinst = (SSAGetInstruction) inst;
                        String fieldname = getinst.getDeclaredField().getName().toString();
                        fieldName.add(fieldname);
                        if(varMap.containsKey(fieldname)) {
                            this.ParamValue.add(varMap.get(fieldname));
                            break;
                        }
                    }

                    if(inst instanceof SSAPutInstruction){
                        SSAPutInstruction putinst = (SSAPutInstruction) inst;
                        int val = putinst.getVal();
                        if(st.isConstant(val)){
                            this.ParamValue.add(varMap.get(st.getConstantValue(val)));
                            continue;
                        }
                        else{
                            // bits.add(val);
                            if(du.getDef(val)!=null) {
                                definsts.add(du.getDef(val));//find the def of the the inConstant use
                                continue;
                            }
                        }
                    }

                    for (int j = 0; j < inst.getNumberOfDefs(); j++) {
                        uses.remove(inst.getDef(j));
                    }

                    for (int j = 0; j < inst.getNumberOfUses(); j++) {
                        if (j == 0 && ((inst instanceof SSAInvokeInstruction
                                && !((SSAInvokeInstruction) inst).isStatic()) || !(inst instanceof SSAAbstractInvokeInstruction))) continue;
                        if (!st.isConstant(inst.getUse(j))) {
                            uses.add(inst.getUse(j));
                            if (du.getDef(inst.getUse(j)) != null) definsts.add(du.getDef(inst.getUse(j)));
                        } else {
                            System.out.println("\t" + inst.getUse(j) + " " + st.getConstantValue(inst.getUse(j)));
                        }
                    }

                }
            }

        }
    }

    // here is the interface for filter out the unrelated statement
    public void filterStatement(Collection<Statement> relatedStmts){
        for (Statement stmt: relatedStmts) {
            if (!stmt.getNode().getMethod().getDeclaringClass().getClassLoader().getName().equals("Primordial")) {
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

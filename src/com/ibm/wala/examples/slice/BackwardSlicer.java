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
import com.ibm.wala.ipa.slicer.NormalStatement;
import com.ibm.wala.ipa.slicer.SDG;
import com.ibm.wala.ipa.slicer.Slicer;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.intset.IntSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class BackwardSlicer {

    private List<Object> ParamValue = new ArrayList<>();

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
        for (Statement stmt: relatedStmts) {
            if (stmt.getNode().getMethod().getDeclaringClass().getClassLoader().getName().equals("Primordial")) {
                relatedStmts.remove(stmt);
            }
        }

        for (int i = 0; i<targetStmt.getNode().getMethod().getNumberOfParameters(); i++) {
            getParameter(i);
        }
    }

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

    /**
        This function is to get the parameter value.
        It will contains three situations:
        TODO:
            1. the use-index is already in the symboltable.
            2. Intra-procedure case
            3. Inter-procedure case.
            4. Static / class field case.
            5. MultiClass Case.
     */
    public void getParameter(int i) {

    }
}

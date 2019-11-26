/*******************************************************************************
 * Copyright (c) 2002 - 2006 IBM Corporation.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package com.ibm.wala.examples.slice;

import com.ibm.wala.cast.java.loader.JavaSourceLoaderImpl;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.examples.ExampleUtil;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.ipa.slicer.Slicer.ControlDependenceOptions;
import com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions;
import com.ibm.wala.ipa.slicer.Statement.Kind;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphIntegrity;
import com.ibm.wala.util.graph.GraphIntegrity.UnsoundGraphException;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.io.CommandLine;
import com.ibm.wala.viz.NodeDecorator;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

/**
 * This simple example WALA application computes a slice (see {@link Slicer}) and fires off the PDF viewer to view a dot-ted
 * representation of the slice.
 * <p>
 * This is an example program on how to use the slicer.
 * <p>
 * See the 'PDFSlice' launcher included in the 'launchers' directory.
 *
 * @author sfink
 * @see Slicer
 */
public class PDFSlice {

    /**
     * Name of the postscript file generated by dot
     */
    private final String PDF_FILE = "slice.pdf";
    public Set<String> parameterList = new HashSet<>();
    public String[] args;
    public String methodT; //method type
    public Map<String, String> paramMap = new HashMap<>();
    public List<String> paramList = new ArrayList<>();

    public PDFSlice(String str, String type) {
        args = str.split(" ");
        methodT = type;
    }

    /**
     * Usage: PDFSlice -appJar [jar file name] -mainClass [main class] -srcCaller [method name] -srcCallee [method name] -dd [data
     * dependence options] -cd [control dependence options] -dir [forward|backward]
     *
     * <ul>2
     * <li>"jar file name" should be something like "c:/temp/testdata/java_cup.jar"
     * <li>"main class" should be something like "c:/temp/testdata/java_cup.jar"
     * <li>"method name" should be the name of a method. This takes a slice from the statement that calls "srcCallee" from "srcCaller"
     * <li>"data dependence options" can be one of "-full", "-no_base_ptrs", "-no_base_no_heap", "-no_heap",
     * "-no_base_no_heap_no_cast", or "-none".
     * </ul>
     *
     * @throws CancelException
     * @throws IllegalArgumentException
     * @throws IOException
     * @see com.ibm.wala.ipa.slicer.Slicer.DataDependenceOptions <li>"control dependence options" can be "-full" or "-none" <li>the
     * -dir argument tells whether to compute a forwards or backwards slice. </ul>
     */

    public Process run() throws IllegalArgumentException, CancelException, IOException {
        // parse the command-line into a Properties object
        Properties p = CommandLine.parse(args);
        // validate that the command-line has the expected format
        validateCommandLine(p);
        // run the applications
        return run(p.getProperty("appJar"), p.getProperty("mainClass"), p.getProperty("srcCaller"), p.getProperty("srcCallee"),
                goBackward(p), getDataDependenceOptions(p), getControlDependenceOptions(p));
    }

    /**
     * Should the slice be a backwards slice?
     */
    private boolean goBackward(Properties p) {
        return !p.getProperty("dir", "backward").equals("forward");
    }

    /**
     * Compute a slice from a call statements, dot it, and fire off the PDF viewer to visualize the result
     *
     * @param appJar     should be something like "c:/temp/testdata/java_cup.jar"
     * @param mainClass  should be something like "c:/temp/testdata/java_cup.jar"
     * @param srcCaller  name of the method containing the statement of interest
     * @param srcCallee  name of the method called by the statement of interest
     * @param goBackward do a backward slice?
     * @param dOptions   options controlling data dependence
     * @param cOptions   options controlling control dependence
     * @return a Process running the PDF viewer to visualize the dot'ted representation of the slice
     * @throws CancelException
     * @throws IllegalArgumentException
     */
    public Process run(String appJar, String mainClass, String srcCaller, String srcCallee, boolean goBackward,
                       DataDependenceOptions dOptions, ControlDependenceOptions cOptions) throws IllegalArgumentException, CancelException,
            IOException {
        try {
            System.out.println("this is for analysis the STRING parameter: ---------------------------------------");
            // create an analysis scope representing the appJar as a J2SE application
            //AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar, (new FileProvider()).getFile(CallGraphTestUtil.REGRESSION_EXCLUSIONS));
            AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(appJar, null);

            //slice 不要进包内slice
            ExampleUtil.addDefaultExclusions(scope);

            // build a class hierarchy, call graph, and system dependence graph
            ClassHierarchy cha = ClassHierarchyFactory.make(scope);
            Iterable<Entrypoint> entrypoints = com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(scope, cha, mainClass);
            AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);
            CallGraphBuilder<InstanceKey> builder = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options, new AnalysisCacheImpl(), cha, scope);
            CallGraph cg = builder.makeCallGraph(options, null);
            PointerAnalysis pa = builder.getPointerAnalysis();
            SDG<InstanceKey> sdg = new SDG<>(cg, builder.getPointerAnalysis(), dOptions, cOptions);
            Statement s = null;
            for (CGNode node: cg) {
                Statement statement = findCallTo(node, srcCallee, methodT);
                if (statement != null) {
                    s = statement;
                }
            }
            System.err.println("Statement: " + s);
            Collection<Statement> slice = null;
            final PointerAnalysis<InstanceKey> pointerAnalysis = builder.getPointerAnalysis();
            slice = Slicer.computeBackwardSlice(s, cg, pointerAnalysis, dOptions, cOptions);
            dumpSlice(slice);

            // create a view of the SDG restricted to nodes in the slice
            Graph<Statement> g = pruneSDG(sdg, slice);
            sanityCheck(slice, g);
            Statement keyStatement = null;
            boolean flag = false;

            //find parameter
            this.findParameter(slice, s, srcCallee);

            //check if keys of symboltable is not changed
            for (Statement st: g) {
                SymbolTable symbolTable = st.getNode().getIR().getSymbolTable();
                System.out.println(st);
                for (int i = 1; i < symbolTable.getMaxValueNumber(); i++) {
                    if (symbolTable.isConstant(i)) {
                        System.out.println(symbolTable.getValueString(i));
                    }
                }
                System.out.println("----");
            }
            System.out.println("PARAMETERS: ");
            for (Map.Entry<String, String> entry: paramMap.entrySet()) {
                System.out.println(entry.getKey() + ": " + entry.getValue());
            }
            return null;
        } catch (WalaException e) {
            // something bad happened.
            e.printStackTrace();
            return null;
        }
    }


    /**
     * check that g is a well-formed graph, and that it contains exactly the number of nodes in the slice
     */
    private void sanityCheck(Collection<Statement> slice, Graph<Statement> g) {
        try {
            GraphIntegrity.check(g);
        } catch (UnsoundGraphException e1) {
            e1.printStackTrace();
            Assertions.UNREACHABLE();
        }
        Assertions.productionAssertion(g.getNumberOfNodes() == slice.size(), "panic " + g.getNumberOfNodes() + " " + slice.size());
    }

    /**
     * If s is a call statement, return the statement representing the normal return from s
     */
    public Statement getReturnStatementForCall(Statement s) {
        if (s.getKind() == Kind.NORMAL) {
            NormalStatement n = (NormalStatement) s;
            SSAInstruction st = n.getInstruction();
            if (st instanceof SSAInvokeInstruction) {
                SSAAbstractInvokeInstruction call = (SSAAbstractInvokeInstruction) st;
                if (call.getCallSite().getDeclaredTarget().getReturnType().equals(TypeReference.Void)) {
                    throw new IllegalArgumentException("this driver computes forward slices from the return value of calls.\n" + ""
                            + "Method " + call.getCallSite().getDeclaredTarget().getSignature() + " returns void.");
                }
                return new NormalReturnCaller(s.getNode(), n.getInstructionIndex());
            } else {
                return s;
            }
        } else {
            return s;
        }
    }

    /**
     * return a view of the sdg restricted to the statements in the slice
     */
    public Graph<Statement> pruneSDG(SDG<InstanceKey> sdg, final Collection<Statement> slice) {
        return GraphSlicer.prune(sdg, slice::contains);
    }

    /**
     * @return a NodeDecorator that decorates statements in a slice for a dot-ted representation
     */
    public NodeDecorator<Statement> makeNodeDecorator() {
        return s -> {
            switch (s.getKind()) {
                case HEAP_PARAM_CALLEE:
                case HEAP_PARAM_CALLER:
                case HEAP_RET_CALLEE:
                case HEAP_RET_CALLER:
                    HeapStatement h = (HeapStatement) s;
                    return s.getKind() + "\\n" + h.getNode() + "\\n" + h.getLocation();
                case NORMAL:
                    NormalStatement n = (NormalStatement) s;
                    SymbolTable symbolTable = n.getNode().getIR().getSymbolTable();
                    //System.out.println("this is parameter value numbers:"+ symbolTable.);
                    //System.out.println("this is parameter 1:" + symbolTable.getParameter(1));
                    System.out.println("test parameter:"+ n.getNode().getIR().getMethod().getParameterType(0).getName());
                    // n.getNode().getIR().getParameter(0);
                    for (int i = 1; i < symbolTable.getMaxValueNumber(); i++) {
                        if (symbolTable.isConstant(i)) {
                            System.out.println(symbolTable.getConstantValue(i));
                            if (symbolTable.getConstantValue(i) instanceof String)
                                parameterList.add((String) symbolTable.getConstantValue(i));
                        }
                    }
                    return n.getInstruction() + "\\n" + n.getNode().getMethod().getSignature()
                            + "\\nLine Number: " + n.getNode().getMethod().getLineNumber(n.getInstructionIndex());
                case PARAM_CALLEE:
                    ParamCallee paramCallee = (ParamCallee) s;

                    return s.getKind() + " " + paramCallee.getValueNumber() + "\\n" + s.getNode().getMethod().getName();
                case PARAM_CALLER:
                    ParamCaller paramCaller = (ParamCaller) s;
                    SymbolTable symbolTable1 = s.getNode().getIR().getSymbolTable();
                    for (int i = 1; i < symbolTable1.getMaxValueNumber(); i++) {
                        if (symbolTable1.isConstant(i)) {
                            System.out.println(symbolTable1.getConstantValue(i));
                            if (symbolTable1.getConstantValue(i) instanceof String)
                                parameterList.add((String) symbolTable1.getConstantValue(i));
                        }
                    }
                    return s.getKind() + " " + paramCaller.getValueNumber() + "\\n" + s.getNode().getMethod().getName() + "\\n"
                            + paramCaller.getInstruction().getCallSite().getDeclaredTarget().getName();
                case EXC_RET_CALLEE:
                case EXC_RET_CALLER:
                case NORMAL_RET_CALLEE:
                case NORMAL_RET_CALLER:
                case PHI:
                default:
                    return s.toString();
            }
        };
    }

    /**
     * Validate that the command-line arguments obey the expected usage.
     * <p>
     * Usage:
     * <ul>
     * <li>args[0] : "-appJar"
     * <li>args[1] : something like "c:/temp/testdata/java_cup.jar"
     * <li>args[2] : "-mainClass"
     * <li>args[3] : something like "Lslice/TestRecursion" *
     * <li>args[4] : "-srcCallee"
     * <li>args[5] : something like "print" *
     * <li>args[4] : "-srcCaller"
     * <li>args[5] : something like "main"
     * </ul>
     *
     * @throws UnsupportedOperationException if command-line is malformed.
     */
    void validateCommandLine(Properties p) {
        if (p.get("appJar") == null) {
            throw new UnsupportedOperationException("expected command-line to include -appJar");
        }
        if (p.get("mainClass") == null) {
            throw new UnsupportedOperationException("expected command-line to include -mainClass");
        }
        if (p.get("srcCallee") == null) {
            throw new UnsupportedOperationException("expected command-line to include -srcCallee");
        }
        if (p.get("srcCaller") == null) {
            throw new UnsupportedOperationException("expected command-line to include -srcCaller");
        }
    }

    //TODO: 我改的可以找类型的方法。methodType 是函数类型。传什么参数都可以，你可以自己看着传。你方便怎么写就怎么来
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

    public DataDependenceOptions getDataDependenceOptions(Properties p) {
        String d = p.getProperty("dd", "full");
        for (DataDependenceOptions result : DataDependenceOptions.values()) {
            if (d.equals(result.getName())) {
                return result;
            }
        }
        Assertions.UNREACHABLE("unknown data datapendence option: " + d);
        return null;
    }

    public ControlDependenceOptions getControlDependenceOptions(Properties p) {
        String d = p.getProperty("cd", "full");
        for (ControlDependenceOptions result : ControlDependenceOptions.values()) {
            if (d.equals(result.getName())) {
                return result;
            }
        }
        Assertions.UNREACHABLE("unknown control datapendence option: " + d);
        return null;
    }

    public void dumpSlice(Collection<Statement> slice) {
        dumpSlice(slice, new PrintWriter(System.err));
    }

    public void dumpSlice(Collection<Statement> slice, PrintWriter w) {
        w.println("SLICE:\n");
        int i = 1;

        for (Statement s : slice) {
            if (s.getKind() == Statement.Kind.NORMAL) { // ignore special kinds of statements
                int bcIndex, instructionIndex = ((NormalStatement) s).getInstructionIndex();
                try {
                    bcIndex = ((ShrikeBTMethod) s.getNode().getMethod()).getBytecodeIndex(instructionIndex);
                    try {
                        int src_line_number = s.getNode().getMethod().getLineNumber(bcIndex);
                        System.err.println ( "Source line number = " + src_line_number );
                    } catch (Exception e) {
                        System.err.println("Bytecode index no good");
                        System.err.println(e.getMessage());
                    }
                } catch (Exception e ) {
                    System.err.println("it's probably not a BT method (e.g. it's a fakeroot method)");
                    System.err.println(e.getMessage());
                }
            }
            String line = (i++) + "   " + s;
            w.println(line);
            w.flush();
        }
    }

    private void findParameter(Collection<Statement> statements, Statement keyStatement, String methodName) {
        //no need to loop
        if (statements.size() == 1) {
            IR ir = keyStatement.getNode().getIR();
            SSAInstruction in = ((StatementWithInstructionIndex) keyStatement).getInstruction();
            if(in instanceof SSAInvokeInstruction) {
                SSAInvokeInstruction call = (SSAInvokeInstruction) in;
                if (call.getCallSite().getDeclaredTarget().getName().toString().equals(methodName)
                        && call.getCallSite().getDeclaredTarget().getSignature().contains(this.methodT)) {
                    for (int i = 0; i<call.getNumberOfUses(); i++) {
                        paramMap.put("#" + i, ir.getSymbolTable().getStringValue(call.getUse(i)));
                        paramList.add(ir.getSymbolTable().getStringValue(call.getUse(i)));
                    }
                }

            }
            return;
        }

        for (Statement st: statements) {
            IR ir = st.getNode().getIR();
            if (st instanceof StatementWithInstructionIndex) {
                SSAInstruction inst = ((StatementWithInstructionIndex) st).getInstruction();
                for (int i = 0; i < inst.getNumberOfUses(); i++) {
                    int usedVar = inst.getUse(i);
                    System.out.println(usedVar);
                    SymbolTable symbolTable = ir.getSymbolTable();
                    if (symbolTable.isConstant(inst.getUse(i))) {
//                        System.out.println(symbolTable.getStringValue(inst.getUse(i)));
                        if (ir.getLocalNames(inst.iIndex(), usedVar) != null) {
                            this.paramMap.put(ir.getLocalNames(inst.iIndex(), usedVar)[0], symbolTable.getStringValue(inst.getUse(i)));
                        }
                        else {
                            this.paramMap.put("#" + i, symbolTable.getStringValue(inst.getUse(i)));
                        }
                    }
                }
            }
        }
    }
}

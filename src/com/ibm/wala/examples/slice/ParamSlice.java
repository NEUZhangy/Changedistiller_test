package com.ibm.wala.examples.slice;

import com.ibm.wala.classLoader.*;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.examples.ExampleUtil;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.FakeRootMethod;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphIntegrity;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.io.CommandLine;
import org.eclipse.core.internal.jobs.InternalJobGroup;
import sun.awt.Symbol;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class ParamSlice {
    public String[] args;
    public String methodT; //method type
    public Map<String, String> paramMap = new HashMap<>();
    public List<Object> paramList = new ArrayList<>();

    public ParamSlice(String str, String type) {
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
                       Slicer.DataDependenceOptions dOptions, Slicer.ControlDependenceOptions cOptions) throws IllegalArgumentException, CancelException,
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
            options.setReflectionOptions(AnalysisOptions.ReflectionOptions.FULL);
            CallGraphBuilder<InstanceKey> builder = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options, new AnalysisCacheImpl(), cha, scope);
            CallGraph cg = builder.makeCallGraph(options, null);
            PointerAnalysis pa = builder.getPointerAnalysis();
            SDG<InstanceKey> sdg = new SDG<>(cg, builder.getPointerAnalysis(), dOptions, cOptions);
            Statement s = null;
            CGNode keyNode = null;
            IClassLoader loader = null;
            for (CGNode node: cg) {
                loader = node.getMethod().getDeclaringClass().getClassLoader();
                System.out.println(loader.getName().toString());
                //loader = node.getClass().getClassLoader();
                if (!loader.toString().contains("Application"))
                    continue;
                IClass iclass = node.getMethod().getDeclaringClass();
                Collection<IField> sFields = iclass.getAllStaticFields();
                for (IField sField : sFields) {
                    System.out.println(sField.getName().toString());
                }
                Collection<IField> iFields = iclass.getAllInstanceFields();
                PDG pdg = sdg.getPDG(node);
                Statement statement = findCallTo( sdg, pdg, node, srcCallee, methodT);
                if (statement != null) {
                    s = statement;
                    keyNode = node;
                    break;
                }
            }
            System.err.println("Statement: " + s);
            Collection<Statement> slice = null;
            final PointerAnalysis<InstanceKey> pointerAnalysis = builder.getPointerAnalysis();
            slice = Slicer.computeBackwardSlice(s, cg, pointerAnalysis, dOptions, cOptions);
            //dumpSlice(slice);

            // create a view of the SDG restricted to nodes in the slice
            Graph<Statement> g = pruneSDG(sdg, slice);
            sanityCheck(slice, g);
//            for (Statement statement:g){
//                try {
//                    SSAInstruction ssain = ((StatementWithInstructionIndex) statement).getInstruction();
//                    int linenumber = statement.getNode().getMethod().getLineNumber(ssain.iIndex());
//                    if(linenumber == 12 ){
//                        System.out.println(statement);
//                    }
//                } catch (Exception e){}
//            }
            //find parameter
            this.findParameter(slice, s, srcCallee, keyNode, sdg.getPDG(keyNode), sdg, g);

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
        } catch (GraphIntegrity.UnsoundGraphException e1) {
            e1.printStackTrace();
            Assertions.UNREACHABLE();
        }
        Assertions.productionAssertion(g.getNumberOfNodes() == slice.size(), "panic " + g.getNumberOfNodes() + " " + slice.size());
    }

    /**
     * return a view of the sdg restricted to the statements in the slice
     */
    public Graph<Statement> pruneSDG(SDG<InstanceKey> sdg, final Collection<Statement> slice) {
        return GraphSlicer.prune(sdg, slice::contains);
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

    //TODO: 我改的可以找类型的方法。methodType
    public Statement findCallTo(SDG sdg, PDG pdg, CGNode n, String methodName, String methodType) {

        IR ir = n.getIR();
        if (ir == null) return null;
        //int nodesize = pdg.getNumberOfNodes();
        //node.getNode().getMethod().getLineNumber(call.iIndex())
        //Statement[] calleestatement = pdg.getParamCalleeStatements();
//        for(Statement s: calleestatement){
//            System.out.println("callee at some where:" + s.getNode());
//        }
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

    public Slicer.DataDependenceOptions getDataDependenceOptions(Properties p) {
        String d = p.getProperty("dd", "full");
        for (Slicer.DataDependenceOptions result : Slicer.DataDependenceOptions.values()) {
            if (d.equalsIgnoreCase(result.getName())) {
                return result;
            }
        }
        Assertions.UNREACHABLE("unknown data datapendence option: " + d);
        return null;
    }

    public Slicer.ControlDependenceOptions getControlDependenceOptions(Properties p) {
        String d = p.getProperty("cd", "full");
        for (Slicer.ControlDependenceOptions result : Slicer.ControlDependenceOptions.values()) {
            if (d.equalsIgnoreCase(result.getName())) {
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

    private void findParameter(Collection<Statement> statements, Statement keyStatement, String methodName,
                               CGNode keyNode, PDG pdg, SDG sdg, Graph<Statement> graph) {
        // First check if the keyStatement can already get the parameter
        int size = statements.size();
        SSAInstruction inst = ((StatementWithInstructionIndex) keyStatement).getInstruction();
        //List<Integer> uses = new ArrayList<>();
        Set<Integer> uses = new HashSet<>();
        if (inst instanceof SSAInvokeInstruction) {
            int i = ((SSAInvokeInstruction) inst).isStatic() == true? 0: 1;
            SymbolTable st = keyStatement.getNode().getIR().getSymbolTable();
            while (i < inst.getNumberOfUses()) {
                int use = inst.getUse(i);
                if (st.isConstant(use)) {
                    paramList.add(st.getConstantValue(use));
                }
                else {
                    uses.add(use);
                }
                i++;
            }
        }
        //get the previous call node
        System.out.print("uses remain:");
        uses.stream().forEach(x-> System.out.print(x + " "));
        System.out.println();
        System.out.println("GET THE PRECEDENCE NODE");
        traverseTargetNode(keyStatement, pdg, uses, sdg, graph);

    }

    public void traverseTargetNode(Statement keyStatement, PDG pdg, Set<Integer> uses, SDG sdg, Graph<Statement> g) {
        Set<Integer> defs = new HashSet<>();
        Set<Statement> visitedStatement = new HashSet<>();
        Queue<Statement> q = new LinkedList<>();
        q.add(keyStatement);
        visitedStatement.add(keyStatement);
        while (!q.isEmpty()) {
            Statement node = q.poll();
            System.out.println("NODE:" + node);

            for (Iterator<? extends Statement> it = g.getPredNodes(node); it.hasNext(); ) {
                Statement s = it.next();
                //filter the node which don't need to check
                if(!s.getNode().getMethod().getDeclaringClass().getClassLoader().getName().toString().contains("Application")) continue;
                if(!visitedStatement.contains(s)){
                    q.add(s);
                    visitedStatement.add(s);
                }
            }
            SymbolTable sy = node.getNode().getIR().getSymbolTable();
            try {
                SSAInstruction call = ((StatementWithInstructionIndex) node).getInstruction();
                System.out.println("Line number:" + node.getNode().getMethod().getLineNumber(call.iIndex()));
                //debug use;
                for (int i = 0; i <= sy.getMaxValueNumber(); i++) {
                    if (sy.isConstant(i)) {
                        System.out.println(i + " " + sy.getConstantValue(i));
                    }
                }

                System.out.println("--DEF--");
                for (int i = 0; i < call.getNumberOfDefs(); i++) {
                    //def.add(call.getDef(i));
                    System.out.println(call.getDef(i));
                    int def= call.getDef(i);
                    if(uses.contains(call.getDef(i))){
                        uses.remove(new Integer(def));
                    }
                    defs.add(def);
                }
                Iterator<Integer> it = defs.iterator();
                while(it.hasNext()) {
                    int tmp = it.next();
                    uses.remove(tmp);
                }

                System.out.println("--USE--");
                for (int i = 0; i < call.getNumberOfUses(); i++) {
                    int varUse = call.getUse(i);
                    System.out.println(varUse);
                    if (sy.isConstant(varUse) && uses.contains(varUse)) {
                        Object constantValue = sy.getConstantValue(varUse);
//                    paramMap.put("#" + i, constantValue);
//                    paramList.add(constantValue);
                        paramList.add(constantValue);
                        uses.remove(new Integer(varUse));
                    } else {
                        uses.add(varUse);
                        //params.add(varUse);
                    }
                }
            } catch (Exception e) {
                System.err.println("NODE WITH FAILURE:" + node);
            }

            if(uses.isEmpty())
                break;
        }
    }
}

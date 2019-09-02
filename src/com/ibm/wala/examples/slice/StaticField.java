package com.ibm.wala.examples.slice;

import com.ibm.wala.cast.ir.ssa.AbstractReflectiveGet;
import com.ibm.wala.cast.ir.ssa.AstGlobalRead;
import com.ibm.wala.cast.ir.ssa.analysis.LiveAnalysis;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.classLoader.ShrikeBTMethod;
import com.ibm.wala.classLoader.ShrikeCTMethod;
import com.ibm.wala.core.tests.callGraph.CallGraphTestUtil;
import com.ibm.wala.dataflow.IFDS.IdentityFlowFunction;
import com.ibm.wala.examples.ExampleUtil;
import com.ibm.wala.examples.analysis.dataflow.ContextInsensitiveReachingDefs;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.PointerKey;
import com.ibm.wala.ipa.cfg.BasicBlockInContext;
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.*;
import com.ibm.wala.ssa.analysis.IExplodedBasicBlock;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.WalaException;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphIntegrity;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.intset.BitVectorIntSet;
import com.ibm.wala.util.intset.IntIterator;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.io.CommandLine;

import java.awt.*;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.List;

public class StaticField {
    public String[] args;
    public String methodT; //method type
    public Map<String, String> paramMap = new HashMap<>();
    public List<Object> paramList = new ArrayList<>();
    public Map<Statement, CGNode> mStmtCGnode = new HashMap<>();
    public Set<String> fieldName = new HashSet<>();
    public ClassHierarchy cha;
    private Map<SSAInstruction, Object> instValMap = new HashMap<>();
    private Map<String, Object> varMap = new HashMap<>();


    public StaticField(String str, String type) {
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
            this.cha = cha;
            Iterable<Entrypoint> entrypoints = com.ibm.wala.ipa.callgraph.impl.Util.makeMainEntrypoints(scope, cha, mainClass);
            AnalysisOptions options = CallGraphTestUtil.makeAnalysisOptions(scope, entrypoints);
            CallGraphBuilder<InstanceKey> builder = Util.makeVanillaZeroOneCFABuilder(Language.JAVA, options, new AnalysisCacheImpl(), cha, scope);
            CallGraph cg = builder.makeCallGraph(options, null);
            final PointerAnalysis<InstanceKey> pointerAnalysis = builder.getPointerAnalysis();
            ExplodedInterproceduralCFG icfg = ExplodedInterproceduralCFG.make(cg);
            ContextInsensitiveReachingDefs reachingDefs = new ContextInsensitiveReachingDefs(icfg, cha);

            PointerAnalysis pa = builder.getPointerAnalysis();
            SDG<InstanceKey> sdg = new SDG<>(cg, builder.getPointerAnalysis(), dOptions, cOptions);
            Statement s = null;
            CGNode keyNode = null;
            CGNode out = null, in = null;
            for (CGNode node : cg) {
                if (!node.getMethod().getDeclaringClass().getClassLoader().getName().toString().contains("Application")) continue;
                SymbolTable st = node.getIR().getSymbolTable();
                DefUse du = node.getDU();
                for(SSAInstruction inst: node.getIR().getInstructions()) {
                    if (inst == null) continue;
                    if (inst instanceof SSAPutInstruction) {
                        System.out.println("\t" +"SSAPUTinstruction " + inst);
                        SSAPutInstruction putinst = (SSAPutInstruction) inst;
                        int use = ((SSAPutInstruction)inst).getUse(0);
                        if (st.isConstant(use)) {
                            varMap.put(putinst.getDeclaredField().getName().toString(), st.getConstantValue(use));
                            System.out.println(st.getConstantValue(use));
                        }
                        else {
                            for(SSAInstruction definst = du.getDef(use); definst != null && !st.isConstant(use);) {
                                int start = 1;
                                if (definst instanceof SSAInvokeInstruction) {
                                    SSAInvokeInstruction invoke = (SSAInvokeInstruction) definst;
                                    if (invoke.isStatic()) start = 0;
                                }
                                if (definst instanceof SSAAbstractInvokeInstruction) {
                                    start = 0;
                                }
                                if (definst instanceof SSAGetInstruction) {
                                    String name = ((SSAGetInstruction) definst).getDeclaredField().getName().toString();
                                    System.out.println("GET INST VALUE: " + varMap.get(name));
                                    st.setConstantValue(use, new ConstantValue(varMap.get(name)));
                                    instValMap.put(definst, varMap.get(name));
                                    break;
                                }
                                System.out.println(definst.getNumberOfUses());
                                use = definst.getUse(start);
                            }
                            varMap.put(putinst.getDeclaredField().getName().toString(), st.getConstantValue(use));
                            instValMap.put(inst, st.getConstantValue(use));
//                            System.out.println(st.getConstantValue(use));
                        }
                    }
                    if (inst instanceof SSAGetInstruction) {
                        Object value = "";
                        String name = ((SSAGetInstruction)inst).getDeclaredField().getName().toString();
                        if (varMap.containsKey(name)) value = varMap.get(name);
                        else value = instValMap.get(inst);
                        instValMap.put(inst, value);
//                        System.out.println("\t" +"SSAGETinstruction " + inst + " v:" + value);
                    }
                }
                Statement statement = findCallTo(node, srcCallee, methodT);
                if (statement != null) {
                    s = statement;
                    keyNode = node;
                }
            }
            System.err.println("Statement: " + s);// key statement
            System.out.println(s.getNode().getMethod());
            Collection<Statement> slice = null;
            slice = Slicer.computeBackwardSlice(s, cg, pointerAnalysis, dOptions, cOptions);
            //dumpSlice(slice);
            // create a view of the SDG restricted to nodes in the slice
            Graph<Statement> g = pruneSDG(sdg, slice);
            sanityCheck(slice, g);
            BitVectorIntSet bits = new BitVectorIntSet();
            String selector = null;
            List<Statement> listArr = new ArrayList<>();
            Map<Integer, Integer> paraMap = new HashMap<>();
            Collection<IField> fields= new ArrayList<>();
            List<Statement> getstmts = new ArrayList<>();
            for (Statement stmt: slice) {
                String func = stmt.getNode().getMethod().getReference().getSelector().getName().toString(); //不会重复分析同一个block的statements
                String type = stmt.getNode().getMethod().getReference().getDeclaringClass().getClassLoader().getName().toString();
                if (!type.contains("Application")) continue; //只找application 相关语句
                if (selector == null) {
                    selector = func;
                    listArr.add(stmt);
                }
                else if (selector.compareToIgnoreCase(func) == 0){
                    //System.out.println(stmt);
                    listArr.add(stmt);
                }
                else {
                    fields= stmt.getNode().getIR().getMethod().getDeclaringClass().getAllStaticFields();
                    SSAInstruction inst =  ((StatementWithInstructionIndex) stmt).getInstruction();
                    for(IField f: fields){
                        System.out.println("fieldname"+ f.getName());
                    }
                    processParam(listArr, bits, paraMap, s, fields);
                    listArr.clear();
                    selector = func;
                }
            }
            processParam(listArr, bits, paraMap,s, fields);
            // might be need to loop the getstmts
            //find parameter
            System.out.println(bits.size());
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
                        System.err.println("Source line number = " + src_line_number);
                    } catch (Exception e) {
                        System.err.println("Bytecode index no good");
                        System.err.println(e.getMessage());
                    }
                } catch (Exception e) {
                    System.err.println("it's probably not a BT method (e.g. it's a fakeroot method)");
                    System.err.println(e.getMessage());
                }
            }
            String line = (i++) + "   " + s;
            w.println(line);
            w.flush();
        }
    }

    private void processParam(List<Statement> listArr, BitVectorIntSet bits, Map<Integer,Integer> paraMap, Statement keyStatement, Collection<IField> fields){
        System.out.println("Start processing");
        Set<SSAInstruction> definsts = new HashSet<>();
        for(int i = listArr.size()-1; i>=0; i--) {
            Statement stm = listArr.get(i);
            System.out.println("\tSTMT: " + stm + " " + stm.getNode().getMethod().getDescriptor());
            if (!(stm instanceof StatementWithInstructionIndex)) continue;

            SSAInstruction inst = ((StatementWithInstructionIndex) stm).getInstruction();
            if(inst instanceof SSAArrayStoreInstruction){ //arraystore is another case
                SSAArrayStoreInstruction arraystore = (SSAArrayStoreInstruction) inst;
                if(bits.contains(arraystore.getArrayRef())){
                    definsts.add(inst);
                }
            }

            if(stm.equals(keyStatement) || definsts.contains(inst)) { //直接依赖DU拿deinsts 有局限性，arraystore不能处理，另外static field Du是空
                IR ir = stm.getNode().getIR();
                DefUse du = stm.getNode().getDU();
                SymbolTable st = ir.getSymbolTable();
                System.out.println("\tINSTRUCTIONS: " + inst);
                if(inst instanceof SSAGetInstruction){
                    SSAGetInstruction getinst = (SSAGetInstruction) inst;
                    int a = getinst.getRef();
                    String fieldname = getinst.getDeclaredField().getName().toString();
                    System.out.println("REF: " + a);
                    try{
                        Iterator<FieldReference> it = ((ShrikeCTMethod) ir.getMethod()).getFieldsRead();
                        while(it.hasNext()) {
                            FieldReference fr = it.next();
                            System.out.println(fr.getName());
                        }
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                    for(IField f: fields) {
                        if(f.getName().toString().equals(fieldname)){
                            IField newf = f.getClassHierarchy().resolveField((getinst.getDeclaredField()));
                            System.out.println(newf);
                        }
                        //TODO: using the static field to get the statement
                    }
                    IField field = this.cha.resolveField(getinst.getDeclaredField());
                    fieldName.add(fieldname);
                    System.out.println("StaticField access, can't deal this method");
                    System.out.println("VALUE: " + instValMap.get(inst));
                    continue;
                }
                if(inst instanceof SSAPutInstruction){
                    SSAPutInstruction putinst = (SSAPutInstruction) inst;
                    IField staticField = this.cha.resolveField(putinst.getDeclaredField());
                    System.out.println(staticField);
                    if(fieldName.contains(putinst.getDeclaredField().getName().toString())){
                        int val = putinst.getVal();
                        if(st.isConstant(val)){
                            System.out.println(st.getConstantValue(val));
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
                }

                for (int j = 0; j < inst.getNumberOfDefs(); j++) {
                    bits.remove(inst.getDef(j));
                }

                for (int j = 0; j < inst.getNumberOfUses(); j++) {
                    if (j == 0 && inst instanceof SSAInvokeInstruction
                            && !((SSAInvokeInstruction) inst).isStatic() && !(inst instanceof SSAAbstractInvokeInstruction)) continue;
                    if (!st.isConstant(inst.getUse(j))) {
                        bits.add(inst.getUse(j));
                        if (du.getDef(inst.getUse(j)) != null) definsts.add(du.getDef(inst.getUse(j)));
                    } else {
                        System.out.println("\t" + inst.getUse(j) + " " + st.getConstantValue(inst.getUse(j)));
                    }
                }

            }
        }
        System.out.println("USES ARR: " + bits);
    }
}



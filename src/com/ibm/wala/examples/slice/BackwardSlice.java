package com.ibm.wala.examples.slice;

import com.google.inject.internal.cglib.core.$CollectionUtils;
import com.google.inject.internal.util.$ObjectArrays;
import com.ibm.wala.cast.java.translator.jdt.ecj.ECJClassLoaderFactory;
import com.ibm.wala.cast.tree.CAstType;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.dataflow.IFDS.BackwardsSupergraph;
import com.ibm.wala.dataflow.IFDS.ISupergraph;
import com.ibm.wala.examples.ExampleUtil;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.*;
import com.ibm.wala.ipa.callgraph.pruned.PrunedCallGraph;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.OrdinalSet;
import com.sun.org.apache.bcel.internal.generic.GETFIELD;
import jdk.internal.dynalink.CallSiteDescriptor;

import javax.swing.text.html.HTMLDocument;
import java.io.IOException;
import java.util.*;


public class BackwardSlice {
    private ISupergraph<Statement, PDG<? extends InstanceKey>> backwardSuperGraph;
    private CallGraph completeCG;
    private HeapModel heapModel;
    private SDG<InstanceKey> completeSDG;
    private ClassHierarchy cha;
    private PointerAnalysis<InstanceKey> pa;

    private HashMap<Integer, List<Object>> paramValue = new HashMap<>();
    //private List<Statement> stmtList = new ArrayList<>();// save the filter slice result
    private Set<String> fieldNames = new HashSet<>();
    private Map<String, Object> varMap = new HashMap<>(); //for save the field value
    private Map<SSAInstruction, Object> instValMap = new HashMap<>();
    private Set<Statement> allRelatedStmt = new HashSet<>();
    private List<String> classorder = new ArrayList<>();
    private Map<String, String> classInitmap = new HashMap<>();
    //private Map<String, Map<Integer, List<Object>>> classVarMap = new HashMap<>();
    //private Statement targetStmt;

    //private FieldReference fieldRef;



    public void run(String path,
                    String callee,
                    String functionType
    ) throws IOException, ClassHierarchyException, CancelException {
        Slicer.DataDependenceOptions dataDependenceOptions = Slicer.DataDependenceOptions.FULL;
        Slicer.ControlDependenceOptions controlDependenceOptions = Slicer.ControlDependenceOptions.FULL;
        AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(path, null);
        ExampleUtil.addDefaultExclusions(scope);
        cha = ClassHierarchyFactory.make(scope);
        Set<Entrypoint> entryPoints = new HashSet<>();
        for (IClass klass : cha) {
            if (!klass.isInterface() && !klass.getClassLoader().getName().toString().contains("Primordial")) {
                for (IMethod method : klass.getDeclaredMethods()) {
                    entryPoints.add(new DefaultEntrypoint(method, cha));
                }
            }
        }
//            Iterable<Entrypoint> entryPoints = new AllApplicationEntrypoints(scope, cha);
        AnalysisOptions options = new AnalysisOptions(scope, entryPoints);
        AnalysisCacheImpl cache = new AnalysisCacheImpl();
        CallGraphBuilder<InstanceKey> builder = Util.makeZeroOneCFABuilder(Language.JAVA, options,
                cache, cha, scope);
        completeCG = builder.makeCallGraph(options, null);
        Set<CGNode> keep = new HashSet<>();
        for (CGNode n: completeCG) {
            if (!isPrimordial(n))
                keep.add(n);
        }
        PrunedCallGraph pcg = new PrunedCallGraph(completeCG, keep);
        completeCG = pcg;
        completeSDG = new SDG<>(completeCG, builder.getPointerAnalysis(), dataDependenceOptions, controlDependenceOptions);
        pa = builder.getPointerAnalysis();
        this.heapModel = pa.getHeapModel();
        SDGSupergraph forwards = new SDGSupergraph(completeSDG, true);
        backwardSuperGraph = BackwardsSupergraph.make(forwards);

        for (CGNode node : completeCG) {
            findAllCallTo(node, callee, functionType);
        }

        for (Statement stmt : allRelatedStmt) {
            Statement targetStmt = stmt;
            clearInit();
            cache.clear();
            String className = targetStmt.getNode().getMethod().getDeclaringClass().getName().toString();
//            if (className.compareTo("Lorg/cryptoapi/bench/predictablecryptographickey/PredictableCryptographicKeyABICase3") != 0)
//                continue;
            Collection<CGNode> roots = new ArrayList<>();
            roots.add(targetStmt.getNode());

            Collection<Statement> relatedStmts = Slicer.computeBackwardSlice(targetStmt, completeCG, builder.getPointerAnalysis(),
                    dataDependenceOptions, controlDependenceOptions);
//             Filter all non application stmts
//            Collection<Statement> relatedStmts = Slicer.computeBackwardSlice(completeSDG, targetStmt);
            List<Statement> stmtList = filterStatement(relatedStmts);
            //Graph<Statement> g = pruneCG(completeCG, completeSDG, targetStmt.getNode());
            setParamValue(targetStmt, stmtList);


            for(int i =0;i < paramValue.size(); i++){
                System.out.println("target parameter is : " + paramValue.get(i));
            }
            //Vulnerfinder(stmtList,g,targetStmt, className);
        }

    }


    public void setParamValue(Statement targetStmt, List<Statement> stmtList) {
        /*first round loop must have use, all checked are SSAinvokeinst */
        SSAInstruction targetInst = ((StatementWithInstructionIndex) targetStmt).getInstruction();
        Set<Integer> uses = new HashSet<>();
        CGNode targetNode = targetStmt.getNode();
        IR targetIR = targetNode.getIR();
        SymbolTable st = targetIR.getSymbolTable();
        DefUse du = targetNode.getDU();
        Set<Integer> visited = null;

        if (targetInst instanceof SSAInvokeInstruction) {
            int i = ((SSAInvokeInstruction) targetInst).isStatic() == true ? 0 : 1;
            int neg = ((SSAInvokeInstruction) targetInst).isStatic() == true ? 0 : -1;
            int numOfUse = targetInst.getNumberOfUses();
            //get all parameter, by process one by one
            while (i < numOfUse) {
                List<Object> ans = new ArrayList<>(); //have more possible value;
                int use = targetInst.getUse(i);
                if (st.isConstant(use)) {
                    ans.add(st.getConstantValue(use));
                    paramValue.put(i + neg, ans);
                } else {
                    uses.add(use);
                    uses = getDU(uses, i + neg, st, visited, ans, du);// can't get the parameter within one block;
                    if (!uses.isEmpty()) {
                        List<Statement> stmtInBlock = new ArrayList<>();
                        useCheckHelper(targetStmt, uses, stmtList, visited, ans, i + neg, stmtInBlock);
                        //loopStatementInBlock(targetStmt, uses, stmtInBlock, i + neg);
                    } else return;
                    //setParamValue(targetStmt, uses, stmtInBlock, i + neg);
                }
                i++;
            }
            if (paramValue.size() == numOfUse)
                return;
        }
    }
    public Set<Integer> getDU(Set<Integer> uses, int pos, SymbolTable st, Set<Integer> visited, List<Object> ans, DefUse du) {
        //TODO:if meet with conditional branch, need more process ; use usenum ==0 to set value is true??
        Queue<Integer> q = new LinkedList<>();
        q.addAll(uses);
        while (!q.isEmpty()) {
            Integer use1 = q.poll();
            // if the use has def, check the def's use could be retrival, else, continue this process;
            if (du.getDef(use1) != null) {
                SSAInstruction inst = du.getDef(use1);

                if (inst.getNumberOfUses() == 0 && inst instanceof SSAGetInstruction){
                   // visited.add((Integer) use1);
                    continue;
                }
                if (inst instanceof SSANewInstruction && ((SSANewInstruction)inst).getNewSite().getDeclaredType().getName().toString().contains("SecureRandom")){
                    uses.remove(use1);
                    ans.add("random value");
                    paramValue.put(pos,ans);
                    uses.remove(use1);
                    continue;
                }
                uses.remove(use1);
                for (int j = 0; j < inst.getNumberOfUses(); j++) {
                    int use = inst.getUse(j);
                    Integer use2 = (Integer) use;
                    // 1) if getbyte(), get the first use; 2) if static, j=0 skip, 3) if not constant add into use 4) if constant, add to ans
                    if (j == 0 && (inst instanceof SSAInvokeInstruction)
                            && (((SSAInvokeInstruction) inst).getDeclaredTarget().getSelector().getName().toString().contains("getBytes"))||
                            (((SSAInvokeInstruction) inst).getDeclaredTarget().getSelector().getName().toString().contains("toCharArray"))) {
                        if (!st.isConstant(use)) {
                            uses.add(use);
                            q.add(use);
                        } else {
                            if (uses.isEmpty()) {
                                ans.add(st.getConstantValue(use));
                                this.paramValue.put(pos, ans);
                                //visited.add(use2);
                                break;
                            }
                        }
                        break;
                    }


                    if (j == 0 && !isInstStatic(inst))
                        continue;
                    //not sure it can deal with field value;
                    if (!st.isConstant(use)) {
                        uses.add(use);
                        q.add(use);
                        //if (du.getDef(use) != null) definsts.add(du.getDef(use));
                    } else {
                        if (uses.size() == 0) {
                            ans.add(st.getConstantValue(use));
                            this.paramValue.put(pos, ans);
                           // visited.add(use);
                        }
                    }
                }
            } else continue;
        }

        return uses;
    }

    public void useCheckHelper(Statement targetStmt, Set<Integer> uses, List<Statement> stmtList, Set<Integer> visited, List<Object> ans, int pos, List<Statement> stmtInblock) {
        for (Integer use : uses) {
            stmtInblock = getStmtInBlock(targetStmt.getNode().getMethod().getSignature(), stmtList);
            usechek(use, targetStmt, uses, stmtList, visited, ans, pos, stmtInblock);
        }
    }

    public void usechek(Integer use, Statement targetStmt, Set<Integer> uses, List<Statement> stmtList, Set<Integer> visited, List<Object> ans, int pos, List<Statement> stmtInBlock) {
        Statement tmp = checkPassin(targetStmt, use, uses, stmtInBlock);
        if (tmp != null) {
            targetStmt = getCalleePosition(tmp);
            if (targetStmt != null) {
                /*check stmtlist have the stmt, has, not has*/
                if (stmtList.contains(targetStmt)) {
                    SSAInstruction inst = ((StatementWithInstructionIndex) targetStmt).getInstruction();
                    int newUse = inst.getUse(use - 1);
                    uses.remove(use);
                    uses.add((Integer) newUse);
                    setParamValue(targetStmt, stmtList);
                } else {
                    System.out.println("should loop the pre node with in block");
                }
                //q.add(newUse);
                return;
            } else {
                //no caller found in the cg, let's check constructor;
                SSAInstruction inst = ((StatementWithInstructionIndex) targetStmt).getInstruction();
                uses.add(inst.getUse(0)); //get itself and see the result
                //q.add(inst.getUse(0));
            }
        } else {
            System.out.println("not passin, checkStaticField");
        }

        StatementWithInstructionIndex getFieldStmt = checkStaticField(targetStmt, use, uses, stmtList);
        if (getFieldStmt != null) {
            SSAGetInstruction getinst = (SSAGetInstruction) getFieldStmt.getInstruction();
            int indexnumber = getinst.iIndex();
            FieldReference fieldRef = getinst.getDeclaredField();
            SSAInstruction targetInst = ((StatementWithInstructionIndex) targetStmt).getInstruction();
//            if (!fieldNames.isEmpty())
//                fieldNames.remove(((SSAGetInstruction) targetInst).getDeclaredField().getName().toString());

            String fieldName = fieldRef.getName().toString();
            this.fieldNames.add(fieldName);
            Iterator<Statement> succStatement = backwardSuperGraph.getSuccNodes(getFieldStmt);
            processSuccStmt(targetStmt, fieldName,stmtList,succStatement, uses, visited, ans, pos,stmtInBlock);

        } else {
            System.out.println("not static field, check instance field");
        }

    }
    public void processSuccStmt(Statement targetStmt, String fieldName, List<Statement> stmtList,Iterator<Statement> succStatement, Set<Integer> uses, Set<Integer> visited, List<Object> ans, int pos,List<Statement> stmtInBlock ){
        while(succStatement.hasNext()){
            Statement currStmt = succStatement.next();
            if (currStmt.getKind() == Statement.Kind.HEAP_PARAM_CALLEE){
                HeapStatement.HeapParamCallee heapcallee = (HeapStatement.HeapParamCallee) currStmt;
                System.out.println("It is a passin-static field, find the heap_param_caller");
                Iterator<? extends Statement> HeapCaller = backwardSuperGraph.getCalledNodes(heapcallee);
                processParaCallee(targetStmt, fieldName,stmtList, heapcallee, uses, visited,ans, pos,stmtInBlock);

                return;
            }

            if(currStmt instanceof StatementWithInstructionIndex){
                SSAInstruction curtInst = ((StatementWithInstructionIndex) currStmt).getInstruction();
                if(curtInst instanceof SSAPutInstruction){
                    SSAPutInstruction curPut = (SSAPutInstruction) curtInst;
                    if(curPut.getDeclaredField().getName().toString().compareTo(fieldName) ==0){
                        uses.add(curPut.getVal());
                        targetStmt = currStmt;
                        uses = getDU(uses, pos, currStmt.getNode().getIR().getSymbolTable(), visited, ans, currStmt.getNode().getDU());
                        useCheckHelper(targetStmt, uses, stmtList, visited, ans, pos, stmtInBlock);
                        return;
                    }

                }
            }
        }

    }

    public void processParaCallee(Statement targetStmt, String fieldname, List<Statement> StmtList, HeapStatement.HeapParamCallee heapParaCallee, Set<Integer> uses, Set<Integer> visited, List<Object> ans, int pos,List<Statement> stmtInBlock  ){

        Iterator<Statement> succCallee = backwardSuperGraph.getSuccNodes(heapParaCallee);
        while(succCallee.hasNext()){
            Statement curStmt = succCallee.next();
            if(curStmt.getKind() == Statement.Kind.HEAP_PARAM_CALLER){
                HeapStatement.HeapParamCaller heapParaCaller = (HeapStatement.HeapParamCaller) curStmt;
                processParaCaller(targetStmt, fieldname, StmtList,heapParaCaller, uses, visited,ans, pos, stmtInBlock);
            }
            // any other possible??

        }

    }

    public void processParaCaller(Statement targetStmt, String fieldname, List<Statement> StmtList, HeapStatement.HeapParamCaller heapParamCaller,Set<Integer> uses, Set<Integer> visited, List<Object> ans, int pos,List<Statement> stmtInBlock ){
        Iterator<Statement> succCaller = backwardSuperGraph.getSuccNodes(heapParamCaller);
        while(succCaller.hasNext()){
            Statement currStmt =  succCaller.next();
            if(currStmt.getKind() == Statement.Kind.HEAP_RET_CALLER){
                HeapStatement.HeapReturnCaller heapReturnCallerStmt = (HeapStatement.HeapReturnCaller) currStmt;
                SSAAbstractInvokeInstruction call = heapReturnCallerStmt.getCall();
                String signature = call.getDeclaredTarget().getSignature();
                stmtInBlock.removeAll(stmtInBlock);
                stmtInBlock = getStmtInBlock(signature, StmtList);
                for(int i =0; i<stmtInBlock.size(); i++){
                    Statement returnCallee = stmtInBlock.get(i);
                    if(returnCallee.getKind() == Statement.Kind.HEAP_RET_CALLEE){
                        HeapStatement.HeapReturnCallee  returnCalleeStmt= (HeapStatement.HeapReturnCallee)returnCallee;
                        if(returnCalleeStmt.getLocation().equals(heapReturnCallerStmt.getLocation())){
                            Iterator<Statement> succStmt = backwardSuperGraph.getSuccNodes(returnCalleeStmt);
                            processSuccStmt(returnCallee, fieldname,StmtList, succStmt, uses, visited, ans, pos,stmtInBlock);
                            return;
                        }

                    }
                }

               // backwardSuperGraph.getCalledNodes(heapReturnStmt);
            }
            //TODO: no method call it , not return caller?
        }
    }



    public List<Statement> getStmtInBlock(String signature, List<Statement> StmtList){
        List<Statement> stmtInBloack = new ArrayList<>();
        for(int i =0; i<StmtList.size(); i++){
            Statement stmt = StmtList.get(i);
            if(stmt.getNode().getMethod().getSignature().compareTo(signature) ==0){
                stmtInBloack.add(stmt);
            }
        }
        return stmtInBloack;
    }

    public Statement checkPassin(Statement targetStmt, int use, Set<Integer> uses, List<Statement> stmtList) {
        boolean flag = false;
        String func = targetStmt.getNode().getMethod().getDeclaringClass().getName().toString() + " " +
                targetStmt.getNode().getMethod().getSelector().getName().toString();

        for (int i = 0; i < stmtList.size(); i++) {
            Statement stm = stmtList.get(i);
            String signature = stm.getNode().getMethod().getDeclaringClass().getName().toString() + " " +
                    stm.getNode().getMethod().getSelector().getName().toString();
            if (signature.compareToIgnoreCase(func) != 0) continue;

            if (stm.getKind() == Statement.Kind.PARAM_CALLER) {
                ParamCaller paramCaller = (ParamCaller) stm;
                int valNum = paramCaller.getValueNumber();
                //SymbolTable st = paramCaller.getNode().getIR().getSymbolTable();
                if (use == valNum) {
                    continue;
                }
            }
            // paramter be called by others
            if (stm.getKind() == Statement.Kind.PARAM_CALLEE) {
                ParamCallee paramCallee = (ParamCallee) stm;
                int valnum = paramCallee.getValueNumber();
                if (valnum == use) {
                    uses.remove(use);
                    flag = true;
                }
            }

            if (flag) {
                if (stm.getKind() == Statement.Kind.METHOD_ENTRY) {
                    return stm;
                }
            } else
                continue;

        }
        return null;
    }

    public Statement getCalleePosition(Statement stm) {
        //FilterIterator<?> it = (FilterIterator<?>) backwardSuperGraph.getCalledNodes(stm);
        Iterator<Statement> succ = backwardSuperGraph.getSuccNodes(stm);
        while (succ.hasNext()) {
            Statement s = succ.next();
            if (s.getNode().getMethod().getDeclaringClass().getName().toString().contains("FakeRootClass")) continue;
            return s;
        }
        return null;
    }

    public StatementWithInstructionIndex checkStaticField(Statement targetStmt, int use, Set<Integer> uses, List<Statement> stmtList) {
        String func = targetStmt.getNode().getMethod().getDeclaringClass().getName().toString() + " " +
                targetStmt.getNode().getMethod().getSelector().getName().toString();
        for (int i = 0; i < stmtList.size(); i++) {
            Statement stm = stmtList.get(i);
            String signature = stm.getNode().getMethod().getDeclaringClass().getName().toString() + " " +
                    stm.getNode().getMethod().getSelector().getName().toString();
            if (signature.compareToIgnoreCase(func) != 0) continue;

            if (!(stm instanceof StatementWithInstructionIndex)) continue;

            SSAInstruction inst = ((StatementWithInstructionIndex) stm).getInstruction();
            if (inst instanceof SSAGetInstruction) {
                SSAGetInstruction getInst = (SSAGetInstruction) inst;
                if (getInst.getDef() == use && getInst.isStatic()) {
                    uses.remove(use);
                    if(!fieldNames.isEmpty()) {
                        fieldNames.remove(getInst.getDeclaredField().getName().toString());
                    }
                    return (StatementWithInstructionIndex) stm;
                }
            }
            continue;

        }
        return null;
    }

    public StatementWithInstructionIndex retrivePutStaticStmt(Statement getStmt, String fieldname, Set<String> fieldNames, List<Statement> stmtList, int indexnumber) {

        // getstmt should be set as targetstmt(invoked place) when in different block
        String func = getStmt.getNode().getMethod().getDeclaringClass().getName().toString() + " " +
                getStmt.getNode().getMethod().getSelector().getName().toString();
        IR ir = getStmt.getNode().getIR();
        SymbolTable st = ir.getSymbolTable();
        int bound = stmtList.indexOf(getStmt);
        for (int i = bound; i > 0; i--) {//get the latest put field;
            Statement stm = stmtList.get(i);
            String signature = stm.getNode().getMethod().getDeclaringClass().getName().toString() + " " +
                    stm.getNode().getMethod().getSelector().getName().toString();
            if (signature.compareToIgnoreCase(func) != 0) continue;

            if (stm instanceof StatementWithInstructionIndex) {
                StatementWithInstructionIndex indexput = (StatementWithInstructionIndex) stm;
                SSAInstruction inst = indexput.getInstruction();
                int index = indexput.getInstructionIndex();
                //if find the  paired put, return
                if (inst instanceof SSAPutInstruction && ((SSAPutInstruction) inst).isStatic() && index < indexnumber) {
                    SSAPutInstruction putinst = (SSAPutInstruction) inst;
                    if (putinst.getDeclaredField().getName().toString().compareToIgnoreCase(fieldname) == 0) {
                        this.fieldNames.add(fieldname);
                        int use = putinst.getVal();
                        if (st.isConstant(use)) {
                            varMap.put(putinst.getDeclaredField().getName().toString(), st.getConstantValue(use));
                            this.fieldNames.remove(fieldname);
                            //return indexput;
                            //TODO:here should be figure out, return null?
                        } else {
                            return indexput;
                        }


                    }
                }

            } else continue;
        }
        return null;
    }

    int i = 0;



    public List<Statement> filterStatement(Collection<Statement> relatedStmts) {
        List<Statement> stmtList = new ArrayList<>();
        for (Statement stmt : relatedStmts) {
            if (!stmt.getNode().getMethod().getDeclaringClass().getClassLoader().getName().toString().equals("Primordial")) {
                stmtList.add(stmt);
            }
        }
        return stmtList;
    }

    //find a signle one
    public Statement findCallTo(CGNode n, String methodName, String methodType, String mainclass) {
        IR ir = n.getIR();
        if (ir == null) return null;
        if (ir.getMethod().getDeclaringClass().getName().toString().compareTo(mainclass) != 0) return null;
        for (SSAInstruction s : Iterator2Iterable.make(ir.iterateAllInstructions())) {
            if (s instanceof SSAInvokeInstruction) {
                SSAInvokeInstruction call = (SSAInvokeInstruction) s;
                // Get the information binding
                String methodT = call.getCallSite().getDeclaredTarget().getSignature();
                if (call.getCallSite().getDeclaredTarget().getName().toString().compareTo(methodName) == 0
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

    public void clearInit() {
        varMap.clear();
        instValMap.clear();
        paramValue.clear();
        //stmtList.clear();
        this.classorder.clear();
    }

    /**
     * Get all related target function statements
     *
     * @param n
     * @param methodName
     * @param methodType
     */
    public void findAllCallTo(CGNode n, String methodName, String methodType) {
        IR ir = n.getIR();
        if (ir == null) return;
        for (SSAInstruction s : Iterator2Iterable.make(ir.iterateAllInstructions())) {
            if (s instanceof SSAInvokeInstruction) {
                SSAInvokeInstruction call = (SSAInvokeInstruction) s;
                // Get the information binding
                String methodT = call.getCallSite().getDeclaredTarget().getSignature();
                if (call.getCallSite().getDeclaredTarget().getName().toString().compareTo(methodName) == 0
                        && methodT.contains(methodType)) {
                    // 一个例子
                    //if (call.getCallSite().getDeclaredTarget().getSignature().contains("Cipher")) continue;
                    IntSet indices = ir.getCallInstructionIndices(((SSAInvokeInstruction) s).getCallSite());
                    Assertions.productionAssertion(indices.size() == 1, "expected 1 but got " + indices.size());
                    allRelatedStmt.add(new NormalStatement(n, indices.intIterator().next()));
                }
            }
        }
        //Assertions.UNREACHABLE("failed to find call to " + methodName + " in " + n);
        return;
    }

    public Statement getMethodEntry(Statement targetStmt, List<Statement> stmtInBlock) {
        String signature = targetStmt.getNode().getMethod().getSignature();
        Queue<Statement> q = new LinkedList<>();
        q.add(targetStmt);
        Statement ans = null;
        Set<Statement> visited = new HashSet<>();
        visited.add(targetStmt);
        while(!q.isEmpty()) {
            Statement head = q.poll();
            if (head.getKind() == Statement.Kind.METHOD_ENTRY && head.getNode().getMethod().getSignature().compareToIgnoreCase(signature) == 0) {
                ans = head;
                break;
            }
            else {
                Iterator<Statement> it = completeSDG.getPredNodes(head);
                while(it.hasNext()) {
                    Statement s = it.next();
                    if (!visited.contains(s)) {
                        q.add(s);
                        visited.add(s);
                    }
                }
            }
        }
//        System.out.println(ans);
//        for (int i = 0; i < stmtInBlock.size(); i++) {
//            Statement stm = stmtInBlock.get(i);
//            if (stm.getKind() == Statement.Kind.METHOD_ENTRY && stm.getNode().getMethod().getSignature().compareToIgnoreCase(signature) == 0) {
//                return stm;
//            } else continue;
//        }
        assert(ans != null);
        return ans;
    }

    public boolean isInstStatic(SSAInstruction inst) {
        if (inst instanceof SSAAbstractInvokeInstruction) return true;
        if (inst instanceof SSAInvokeInstruction) return ((SSAInvokeInstruction) inst).isStatic();
        if (inst instanceof SSAGetInstruction) return ((SSAGetInstruction) inst).isStatic();
        if (inst instanceof SSAPutInstruction) return ((SSAPutInstruction) inst).isStatic();
        //abstractinvoke from 0;
        return false;
    }

    public boolean isPrimordial(CGNode n) {
        return n.getMethod().getDeclaringClass().getClassLoader().getName().toString().equals("Primordial");
    }


}

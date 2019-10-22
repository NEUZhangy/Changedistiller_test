
package com.ibm.wala.examples.slice;

import com.google.inject.internal.util.$ObjectArrays;
import com.ibm.wala.classLoader.*;
import com.ibm.wala.examples.ExampleUtil;
import com.ibm.wala.ipa.callgraph.*;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.cha.ClassHierarchy;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.slicer.*;
import com.ibm.wala.ssa.*;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.util.CancelException;
import com.ibm.wala.util.collections.Iterator2Iterable;
import com.ibm.wala.util.config.AnalysisScopeReader;
import com.ibm.wala.util.debug.Assertions;
import com.ibm.wala.util.graph.Graph;
import com.ibm.wala.util.graph.GraphSlicer;
import com.ibm.wala.util.intset.IntSet;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;

    public class BackwardSlicer2 {

        private List<Object> ParamValue = new ArrayList<>();
        private List<Statement> stmtList = new ArrayList<>();// save the filter slice result
        private Set<String> fieldName = new HashSet<>();
        private Map<String, Object> varMap = new HashMap<>();
        private Map<SSAInstruction, Object> instValMap = new HashMap<>();
        private Set<Statement> allRelatedStmt = new HashSet<>();
        private List<String> classorder = new ArrayList<>();

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
            Slicer.DataDependenceOptions dataDependenceOptions = Slicer.DataDependenceOptions.NO_HEAP;
            Slicer.ControlDependenceOptions controlDependenceOptions = Slicer.ControlDependenceOptions.FULL;
            AnalysisScope scope = AnalysisScopeReader.makeJavaBinaryAnalysisScope(path, null);
            ExampleUtil.addDefaultExclusions(scope);
            ClassHierarchy cha = ClassHierarchyFactory.make(scope);
            Set<Entrypoint> entryPoints = new HashSet<>();
            for (IClass klass: cha) {
                if (!klass.isInterface() && !klass.getClassLoader().getName().toString().contains("Primordial")) {
                    for (IMethod method: klass.getDeclaredMethods()) {
                        entryPoints.add(new DefaultEntrypoint(method, cha));
                    }
                }
            }
//            Iterable<Entrypoint> entryPoints = new AllApplicationEntrypoints(scope, cha);
            AnalysisOptions options = new AnalysisOptions(scope, entryPoints);
            AnalysisCacheImpl cache = new AnalysisCacheImpl();
            CallGraphBuilder<InstanceKey> builder = Util.makeZeroOneCFABuilder(Language.JAVA, options,
                    cache, cha, scope);
            CallGraph completeCG = builder.makeCallGraph(options, null);
            SDG<InstanceKey> completeSDG = new SDG<>(completeCG, builder.getPointerAnalysis(), dataDependenceOptions, controlDependenceOptions);
            Set<SSAInstruction> visitedInst = new HashSet<>();

            for (CGNode node: completeCG) {
//            Statement stmt = findCallTo(node, callee, functionType, mainClass);
                findAllCallTo(node, callee, functionType);
//            if (stmt != null) {
//                targetStmt = stmt;
//                break;
//            }
            }

            for (Statement targetStmt: allRelatedStmt) {
                varMap.clear();
                instValMap.clear();
                ParamValue.clear();
                stmtList.clear();
                this.classorder.clear();
                cache.clear();
                String className = targetStmt.getNode().getMethod().getDeclaringClass().getName().toString();
                if (className.compareTo("Lorg/cryptoapi/bench/predictablecryptographickey/Crypto") != 0) continue;
                System.out.println(className);
                Collection<CGNode> roots = new ArrayList<>();
                roots.add(targetStmt.getNode());
                Collection<Statement> relatedStmts = Slicer.computeBackwardSlice(targetStmt, completeCG, builder.getPointerAnalysis(),
                        dataDependenceOptions, controlDependenceOptions);
                Graph<Statement> g = pruneCG(completeCG, completeSDG, targetStmt.getNode());
                List<Statement> sorted_g = new ArrayList<>();
                Map<String, List<Statement>> funMap = new HashMap<>();
                /*
                TODO: add class as prefix
                 */
                for (Statement stmt: g) {
                    String funName = stmt.getNode().getMethod().getReference().toString();
                    List<Statement> l = funMap.get(funName);
                    if (l == null) l = new ArrayList<>();
                    l.add(stmt);
                    funMap.put(funName, l);
                }
                for (String str: classorder) {
                    if (!funMap.containsKey(str)) continue;
                    sorted_g.addAll(funMap.get(str));
                }

                for (Statement stmt : sorted_g) {
                    if (!(stmt instanceof StatementWithInstructionIndex)) continue;
                    SSAInstruction inst = ((StatementWithInstructionIndex) stmt).getInstruction();
                    if (visitedInst.contains(inst)) continue;
//                    System.out.println("\t" + stmt);
                    visitedInst.add(inst);
                    CGNode node = stmt.getNode();
                    SymbolTable st = node.getIR().getSymbolTable();
                    DefUse du = node.getDU();
                    if (inst instanceof SSAPutInstruction) {
                        SSAPutInstruction putinst = (SSAPutInstruction) inst;
                        int use = ((SSAPutInstruction) inst).getUse(0);
                        if (st.isConstant(use)) {
                            varMap.put(putinst.getDeclaredField().getName().toString(), st.getConstantValue(use));
                        } else {
                            for (SSAInstruction definst = du.getDef(use); definst != null && !st.isConstant(use); ) {
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
                                    st.setConstantValue(use, new ConstantValue(varMap.get(name)));
                                    instValMap.put(definst, varMap.get(name));
                                    break;
                                }
                                use = definst.getUse(start);
                            }
                            if (st.isConstant(use)) {
                                varMap.put(putinst.getDeclaredField().getName().toString(), st.getConstantValue(use));
                                instValMap.put(inst, st.getConstantValue(use));
                            }
                        }
                    }
                    if (inst instanceof SSAGetInstruction) {
                        Object value = "";
                        String name = ((SSAGetInstruction) inst).getDeclaredField().getName().toString();
                        if (varMap.containsKey(name)) value = varMap.get(name);
                        else value = instValMap.get(inst);
                        instValMap.put(inst, value);
                    }

                }

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
                    try{
                        getParameter(i+neg);
                    } catch (IndexOutOfBoundsException e) {
                        System.out.println("Index out of bound");
                    } catch (IllegalArgumentException e) {
                        System.err.println(e.getMessage());
                    }
                }
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
                    int before = ParamValue.size();
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
                                //System.out.println(selector);
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
                                stmtInBlock.add(stmt);
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


        //This function should be refactor as follows:
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
                /*
                    There is a situation causing dangling paramcaller which we cannot find the callee to map with it.
                    The reason causing this issue is that the paramcallee has been ignored since its classloader
                    is primordial. Currently, we defined that if we find a paramcaller and the current paramcaller count
                    is equal to paramcallee, then this paramcaller statement is a dangling paramcaller. And the actual
                    use number is useful maybe. And it needs to be taken care of.
                 */
                    if (callerCount < calleeCount) callerCount ++;
                    else {
                        ParamCaller paramCaller = (ParamCaller) stm;
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

                if (inst instanceof SSAGetInstruction || inst instanceof SSAPutInstruction) {
                    if (this.instValMap.containsKey(inst)) {
                        this.ParamValue.add(instValMap.get(inst));
                        break;
                    }
                }

                for (int j = 0; j < inst.getNumberOfDefs(); j++) {
                    uses.remove(inst.getDef(j));
                }

                for (int j = 0; j < inst.getNumberOfUses(); j++) {
                    int use = inst.getUse(j);
                    if (j == 0 && ((inst instanceof SSAInvokeInstruction
                            && !((SSAInvokeInstruction) inst).isStatic()) || !(inst instanceof SSAAbstractInvokeInstruction))
                            && !st.isConstant(use)
                            && !(inst instanceof SSAPutInstruction))
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
                            && methodT.contains(methodType) ) {
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
         * Get all related function statements
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
                            && methodT.contains(methodType) ) {
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

        public Graph<Statement> pruneSDG(SDG<InstanceKey> sdg, String mainClass) {
            Predicate<Statement> ifStmtInBlock = (i) -> (i.getNode().getMethod().getReference().getDeclaringClass().
                    getName().toString().contains(mainClass));
            return GraphSlicer.prune(sdg, ifStmtInBlock);
        }

        public Graph<Statement> pruneSDG(SDG<InstanceKey> sdg, Statement targetStmt) {
            Queue<Statement> stmtQueue = new LinkedList<>();
            stmtQueue.add(targetStmt);
            Set<Statement> relatedStmt = new HashSet<>();
            Set<String> relatedClass = new HashSet<>();
            while (!stmtQueue.isEmpty()) {
                Statement head = stmtQueue.poll();
                if (head.getNode().getMethod().getDeclaringClass().getClassLoader().getName().toString().equals("Primordial"))
                    continue;
                relatedStmt.add(head);
                relatedClass.add(head.getNode().getMethod().getReference().getDeclaringClass().getName().toString());
                Iterator<Statement> itst = sdg.getPredNodes(head);
                while (itst.hasNext()) {
                    Statement stmt = itst.next();
                    if (relatedStmt.contains(stmt)) continue;
                    stmtQueue.add(stmt);
                }
            }
            Predicate<Statement> ifStmtinBlock = (i) -> (relatedClass.contains(i.getNode().getMethod().getReference().
                    getDeclaringClass().getName().toString()));
            return GraphSlicer.prune(sdg, ifStmtinBlock);
        }

        public Graph<Statement> pruneCG(CallGraph cg, SDG<InstanceKey> sdg, CGNode cgnode) {

            Set<CGNode> visited = new HashSet<>();
            Queue<CGNode> nodeQueue = new LinkedList<>();
            Set<String> relatedClass = new HashSet<>();
            nodeQueue.add(cgnode);
            visited.add(cgnode);
            //classorder.add(cgnode.getMethod().getName().toString());
            List<String> tmpClassOrder = new ArrayList<>();
            while(!nodeQueue.isEmpty()) {
                CGNode head = nodeQueue.poll();
//                System.out.println(head);
                Iterator<CGNode> itnode = cg.getPredNodes(head);
                relatedClass.add(head.getMethod().getReference().getDeclaringClass().getName().toString());
                while(itnode.hasNext()) {
                    CGNode n = itnode.next();
//                    System.out.println("\t" + n);
                    tmpClassOrder.clear();
                    if (n.getMethod().getDeclaringClass().getName().toString().contains("FakeRootClass")) continue;
                    tmpClassOrder.add(n.getMethod().getReference().toString());
                    Iterator<CallSiteReference> callIter = n.iterateCallSites();
                    while (callIter.hasNext()) {
                        CallSiteReference csRef = callIter.next();
                        if (csRef.getDeclaredTarget().getName().toString().contains("fakeWorldClinit")) continue;
                        MethodReference mRef = csRef.getDeclaredTarget();
                        if (mRef.getDeclaringClass().getClassLoader().getName().toString().contains("Primordial")) continue;
                        tmpClassOrder.add(mRef.toString());
                    }
                    this.classorder.clear();
                    this.classorder.addAll(tmpClassOrder);
                    if (visited.contains(n)) continue;
                    nodeQueue.add(n);
                    if (!n.getMethod().getDeclaringClass().getName().toString().contains("FakeRootClass")) visited.add(n);
                }
            }
            Set<CGNode> visitedSucc = new HashSet<>();
            for (CGNode node: visited){
                nodeQueue.add(node);
                visitedSucc.add(node);
                while(!nodeQueue.isEmpty()) {
                    CGNode head = nodeQueue.poll();
                    relatedClass.add(head.getMethod().getReference().getDeclaringClass().getName().toString());
//                System.out.println("Current Node: " + head);
                    Iterator<CGNode> itnode = cg.getSuccNodes(head);
//                System.out.println("\tChild Nodes: ");
                    while(itnode.hasNext()) {
                        CGNode n = itnode.next();
//                    System.out.println("\t\t" + n);
                        if (visitedSucc.contains(n)) continue;
                        nodeQueue.add(n);
                        if (!n.getMethod().getDeclaringClass().getName().toString().contains("FakeRootClass")
                        && !n.getMethod().getDeclaringClass().getClassLoader().toString().contains("Primordial")){
                            visitedSucc.add(n);
                        }
                    }
                }
            }

            Predicate<Statement> ifStmtinBlock = (i) ->
                    ((visited.contains(i.getNode()) || visitedSucc.contains(i.getNode()) ||
                        relatedClass.contains(i.getNode().getMethod().getReference().getDeclaringClass().getName().toString()))
                            && !i.getNode().getMethod().getDeclaringClass().getClassLoader().getName().toString().equals("Primordial"));
            return GraphSlicer.prune(sdg, ifStmtinBlock);
        }


    }


package diff;

import com.changedistiller.test.SSLDetect.VariableDeclarationVisitor;
import com.github.javaparser.JavaToken;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Range;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import utils.StringSimilarity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class DiffChecker {
    private TypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver());
    private JavaSymbolSolver symbolSolver = new JavaSymbolSolver(typeSolver);
    private LogManager logManager = LogManager.getLogManager();
    private Logger log = logManager.getLogger(Logger.GLOBAL_LOGGER_NAME);
    public Map<String, TreeSet<Expression>> lVarMap = new TreeMap<String, TreeSet<Expression>>();
    public Map<String, TreeSet<Expression>> rVarMap = new TreeMap<String, TreeSet<Expression>>();
    public Map<Expression, Set<String>> eToVL = new HashMap<>();
    public Map<Expression, Set<String>> eToVR = new HashMap<>();
    public Map<Expression, Boolean> expLable = new HashMap<>();

    public DiffChecker() throws IOException {
        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);
    }

    public void run() throws FileNotFoundException {
        String left_file_path = "C:\\Users\\LinG\\Desktop\\case\\pbei.java";
        String right_file_path = "C:\\Users\\LinG\\Desktop\\case\\pbes.java";
        CompilationUnit leftCU = getCU(left_file_path);
        CompilationUnit rightCU = getCU(right_file_path);
        Map<Range, Expression> leftStmts = getStatements(leftCU);
        Map<Range, Expression> rightStmts = getStatements(rightCU);
        HashMap<Range, Range> lineMap = null;
        extractVars(leftStmts, lVarMap, eToVL);
        extractVars(rightStmts, rVarMap, eToVR);
        DiffType diff = null;
        // 这里需不需要判断相等存疑，可以直接进行匹配
        if (leftStmts.size() == rightStmts.size()) {
            lineMap = getMatchStmts(leftStmts, rightStmts, leftCU, rightCU, 0.69f);
            diff = extractDiff(lineMap, leftStmts, rightStmts);
        } else {
            // 判断多行情况
            lineMap = getMatchStmts(leftStmts, rightStmts, leftCU, rightCU, 0.6f);
            diff = extractMultiDiff(lineMap, leftStmts, rightStmts, leftCU, rightCU);
        }
        if (leftStmts.size() != 0)
            extractTargetStmt(diff, leftCU, rightStmts, lineMap);
        else {
            extractTargetStmt(diff, rightCU, rightStmts, lineMap);
        }
        // 如果方法类型是keypairgenerator，特殊处理
        if (diff.methodType.compareTo("KeyPairGenerator") == 0) {
            extractKPG(diff, leftStmts, leftCU);
        }
        log.info(diff.toString());
    }

    private DiffType extractDiff(
            HashMap<Range, Range> lineMap,
            Map<Range, Expression> leftStmts,
            Map<Range, Expression> rightStmts) {

        for (Range l : lineMap.keySet()) {
            Range r = lineMap.get(l);
            Expression leftExpr = leftStmts.get(l);
            Expression rightExpr = rightStmts.get(r);
            List<Node> leftNodes = extractNode(leftExpr);
            List<Node> rightNodes = extractNode(rightExpr);
            log.info("left: " + leftNodes.size() + " " + " right: " + rightNodes.size());
            assert leftNodes.size() == rightNodes.size()
                    : "leftNodes and rightNodes is not the same length";
            DiffType diff = extractDiff(leftNodes, rightNodes);

            if (diff.pattern != null) {
                if (diff.pattern == Pattern.PARAMETER || diff.pattern == Pattern.NUMBER) {
                    extractPos(leftExpr, diff);
                }
                return diff;
            }
        }
        return new DiffType();
    }

    // get the similat stmt in multi-line case
    private DiffType extractMultiDiff(
            HashMap<Range, Range> lineMap,
            Map<Range, Expression> leftStmts,
            Map<Range, Expression> rightStmts,
            CompilationUnit leftCU,
            CompilationUnit rightCU) {
        DiffType diff = new DiffType();
        diff.stmts = new HashSet<>();
        diff.pattern = Pattern.COMPOSITE;
        Map<Range, Expression> leftStmts_ = new HashMap<>(leftStmts);
        Map<Range, Expression> rightStmts_ = new HashMap<>(rightStmts);
        for (Range l : lineMap.keySet()) {
            Range r = lineMap.get(l);
            leftStmts_.remove(l);
            rightStmts_.remove(r);
        }
        if (leftStmts_.size() > 0) {
            diff.action = Action.DELETE;
        } else if (rightStmts_.size() > 0) {
            diff.action = Action.ADD;
        }
        addStmtToDiff(diff, rightStmts, rightCU);
        return diff;
    }

    private void extractVars(
            Map<Range, Expression> stmts,
            Map<String, TreeSet<Expression>> varMap,
            Map<Expression, Set<String>> etoV) {
        for (Expression e : stmts.values()) {
            Map<String, String> varList = new HashMap<>();
            VoidVisitor<Map<String, String>> visitor = new VariableDeclarationVisitor();
            e.accept(visitor, varList);
            Set<String> vars = ((VariableDeclarationVisitor) visitor).getVariables();
            expLable.put(e, ((VariableDeclarationVisitor) visitor).keyStmt);
            etoV.put(e, vars);
            for (String v : vars) {
                putValue(varMap, v, e);
            }
        }
    }

    private void extractPos(Expression expr, DiffType diff) {
        if (diff.pos != -1) return;
        Set<String> vars = eToVL.get(expr);
        for (String v : vars) {
            TreeSet<Expression> expressions = lVarMap.get(v);
            if (!isSecurityAPI(resolveType(expr))) expressions.remove(expr);
            for (Expression exp : expressions) {
                if (exp instanceof VariableDeclarationExpr) {
                    VariableDeclarationExpr VDExp = (VariableDeclarationExpr) exp;
                    Map<String, String> varList = new HashMap<>();
                    VoidVisitor<Map<String, String>> visitor = new VariableDeclarationVisitor();
                    VDExp.accept(visitor, varList);
                    Integer pos = ((VariableDeclarationVisitor) visitor).getPos(v);
                    diff.pos = pos;
                }
            }
        }
    }

    // extract the detail diff
    private DiffType extractDiff(List<Node> leftNodes, List<Node> rightNodes) {
        for (int i = leftNodes.size() - 1; i >= 0; i--) {
            Node lNode = leftNodes.get(i);
            Node rNode = rightNodes.get(i);

            // 名字比了没意义
            if (lNode instanceof SimpleName) {
                continue;
            }
            // 如果是字符串或者是整数，就直接进行比较，如果不一样，就是参数问题
            if (lNode instanceof IntegerLiteralExpr) {
                log.info("Literal Comparison");
                if (!((IntegerLiteralExpr) lNode)
                        .getValue()
                        .equals(((IntegerLiteralExpr) rNode).getValue())) {
                    log.info("Number Parameter Err");
                    return new DiffType(
                            Pattern.NUMBER,
                            String.valueOf(((IntegerLiteralExpr) lNode).getValue()),
                            String.valueOf(((IntegerLiteralExpr) rNode).getValue()),
                            null,
                            null,
                            extractPosDirectly(lNode),
                            null,
                            null);
                }
            }

            if (lNode instanceof StringLiteralExpr) {
                log.info("Literal Comparison");
                if (!((LiteralStringValueExpr) lNode)
                        .getValue()
                        .equals(((LiteralStringValueExpr) rNode).getValue())) {
                    log.info("String Parameter Err");
                    return new DiffType(
                            Pattern.PARAMETER,
                            String.valueOf(((LiteralStringValueExpr) lNode).getValue()),
                            String.valueOf(((LiteralStringValueExpr) rNode).getValue()),
                            null,
                            null,
                            -1,
                            null,
                            null);
                }
            }

            // 比较方法名不一样
            if (lNode instanceof ClassOrInterfaceType && rNode instanceof ClassOrInterfaceType) {
                log.info("Method Comparison");
                log.info(
                        ((ClassOrInterfaceType) lNode).getName()
                                + " "
                                + ((ClassOrInterfaceType) rNode).getName());
                SimpleName lName = ((ClassOrInterfaceType) lNode).getName();
                SimpleName rName = ((ClassOrInterfaceType) rNode).getName();
                if (!lName.equals(rName)) {
                    log.info("MethodName Err");
                    return new DiffType(
                            Pattern.NAME,
                            String.valueOf(lName.toString()),
                            String.valueOf(rName.toString()),
                            null,
                            null,
                            -1,
                            null,
                            null);
                }
            }
        }
        return new DiffType();
    }

    private void extractKPG(DiffType diff, Map<Range, Expression> stmts, CompilationUnit cu) {
        log.info("Meet KeyPairGenerator");
        // change callee to initialize for backward slicing
        diff.callee = "initialize";
        // need to check the value of kpg.getInstance
        if (diff.pattern == Pattern.NUMBER) diff.pos = 0;
        //        Expression getInstance = null;
        //        for (Expression e: stmts.values()) {
        //            if (e.toString().contains("getInstance")) {
        //                getInstance = e;
        //                break;
        //            }
        //        }
        //        String algo =
        // getInstance.findFirst(MethodCallExpr.class).get().getArgument(0).toString();
        //        System.out.println(algo);
        Map<Range, String> normalizeStmts = normalizeStmts(cu, stmts);
        HashSet<String> matchStmts = new HashSet<>();
        for (String stmt : normalizeStmts.values()) {
            if (stmt.contains("getInstance")) {
                matchStmts.add(stmt);
            }
        }
        diff.stmts = matchStmts;
    }

    private void addStmtToDiff(DiffType diff, Map<Range, Expression> stmts, CompilationUnit cu) {
        Map<Range, String> stmtMap = normalizeStmts(cu, stmts);
        for (String str : stmtMap.values()) {
            diff.stmts.add(str);
        }
    }

    private List<Node> extractNode(Expression expr) {
        List<Node> nodes = new ArrayList<>();
        //        System.out.println(expr);
        expr.stream()
                .forEach(
                        x -> {
                            nodes.add(x);
                            //            System.out.println("\t" + x + " " + x.getClass() + "\n");
                        });

        return nodes;
    }

    private void extractTargetStmt(
            DiffType diff,
            CompilationUnit cu,
            Map<Range, Expression> rightStmts,
            HashMap<Range, Range> lineMap) {
        log.info("extracting the target stmt");
        List<Expression> exps = new ArrayList<>();
        exps.addAll(cu.findAll(ObjectCreationExpr.class));
        exps.addAll(cu.findAll(MethodCallExpr.class));
        for (Expression exp : exps) {
            ResolvedType resolvedType = exp.calculateResolvedType();
            String type = resolvedType.describe();
            if (isSecurityAPI(type)) {
                // 拿到第几个参数
                diff.methodType = type.substring(type.lastIndexOf(".") + 1);
                diff.callee = extractCallee(exp);
                if (diff.pattern == Pattern.COMPOSITE) {
                    diff.pos =
                            extractCompositePos(exp, getRightExp(exp, rightStmts, lineMap), diff);
                }
            }
            if (type.contains("Random")) {
                diff.methodType = type.substring(type.lastIndexOf(".") + 1);
                diff.callee = "<init>";
            }
        }
    }

    private Expression getRightExp(
            Expression exp, Map<Range, Expression> rightStmts, HashMap<Range, Range> lineMap) {
        Node parent = exp.getParentNode().get();
        while (!(parent instanceof ExpressionStmt)) {
            parent = parent.getParentNode().get();
        }
        log.info(parent.toString());
        Expression rightExp = rightStmts.get(lineMap.get(parent.getRange().get()));
        if (rightExp == null) return exp;
        for (Expression e : rightExp.findAll(exp.getClass())) {
            return e;
        }
        return null;
    }

    private int extractCompositePos(Expression left, Expression right, DiffType diff) {
        log.info("Extracting composite pos");
        if (left instanceof ObjectCreationExpr) {
            NodeList<Expression> leftArgs = ((ObjectCreationExpr) left).getArguments();
            NodeList<Expression> rightArgs = ((ObjectCreationExpr) right).getArguments();
            for (int i = 0; i < leftArgs.size(); i++) {
                Expression leftExp = leftArgs.get(i);
                Expression rightExp = rightArgs.get(i);
                if (leftExp.getClass() != rightExp.getClass()) {
                    diff.incorrect = "static";
                    return i;
                }
            }
        }

        if (left instanceof MethodCallExpr) {
            NodeList<Expression> leftArgs = ((MethodCallExpr) left).getArguments();
            NodeList<Expression> rightArgs = ((MethodCallExpr) right).getArguments();
            for (int j = 0; j < leftArgs.size(); j++) {
                Expression leftExp = leftArgs.get(j);
                Expression rightExp = rightArgs.get(j);
                if (leftExp.getClass() == rightExp.getClass()) {
                    // diff.incorrect ="static";
                    return j;
                }
            }
        }
        return -1;
    }

    private CompilationUnit getCU(String file_path) throws FileNotFoundException {
        return StaticJavaParser.parse(new File(file_path));
    }

    private Map<Range, Expression> getStatements(CompilationUnit cu) {
        Map<Range, Expression> stmtMap = new HashMap<>();
        cu.findAll(ExpressionStmt.class)
                .forEach(x -> stmtMap.put(x.getRange().get(), x.getExpression()));
        return stmtMap;
    }

    private HashMap<Range, Range> getMatchStmts(
            Map<Range, Expression> left,
            Map<Range, Expression> right,
            CompilationUnit leftCU,
            CompilationUnit rightCU,
            float threshold) {
        HashMap<Range, Range> lineMap = new HashMap<>();
        StringSimilarity ss = new StringSimilarity();
        Map<Range, String> leftStmts = normalizeStmts(leftCU, left);
        Map<Range, String> rightStmts = normalizeStmts(rightCU, right);
        for (Range i : leftStmts.keySet()) {
            for (Range j : rightStmts.keySet()) {
                String leftStmt = leftStmts.get(i);
                String rightStmt = rightStmts.get(j);
                float score = (float) ss.calculateSimilarity(leftStmt, rightStmt);
                //                System.out.println(leftStmt + "-" + rightStmt + ":" + score +
                // "\n");
                if (score >= threshold) {
                    //                    System.out.println(leftStmt + right);
                    lineMap.put(i, j);
                    break;
                }
            }
        }
        return lineMap;
    }

    private Map<Range, String> normalizeStmts(CompilationUnit cu, Map<Range, Expression> lineMap) {
        Map<String, String> paramMap = new HashMap<>();
        Map<String, String> varList = new HashMap<>();
        VoidVisitor<Map<String, String>> visitor = new VariableDeclarationVisitor();
        cu.accept(visitor, varList);
        log.info(varList.toString());
        Set<Integer> lineNumSet = new HashSet<>();
        Map<Integer, Range> lineRangeMap = new HashMap<>();
        for (Range r : lineMap.keySet()) {
            lineNumSet.add(r.begin.line);
            lineRangeMap.put(r.begin.line, r);
        }
        for (Map.Entry<String, String> entry : varList.entrySet()) {
            paramMap.put("\\" + entry.getValue(), entry.getKey());
        }
        int prev = -1;
        Map<Range, String> matchingStmt = new HashMap<>();

        String str = "";

        for (JavaToken tr : cu.getTokenRange().get()) {
            int nline = tr.getRange().get().begin.line;
            if (lineNumSet.contains(nline)) {
                if (prev == -1) prev = nline;
                if (prev != nline) {
                    matchingStmt.put(lineRangeMap.get(prev), str.trim());
                    str = "";
                    prev = nline;
                }
                if (varList.containsKey(tr.getText())) {
                    str += varList.get(tr.getText());
                } else {
                    str += tr.getText();
                }
            }
        }
        matchingStmt.put(lineRangeMap.get(prev), str.trim());
        return matchingStmt;
    }

    private boolean isSecurityAPI(String type) {
        Set<String> keywords = new HashSet<>(Arrays.asList("security", "crypto", "javax"));
        for (String keyword : keywords) {
            if (type.contains(keyword)) return true;
        }
        return false;
    }

    private String extractCallee(Expression exp) {
        log.info("Extract callee");
        if (exp instanceof ObjectCreationExpr) {
            return "<init>";
        }
        if (exp instanceof MethodCallExpr) {
            // ResolvedType type = (MethodCallExpr) exp.calculateResolvedType().describe();
            return ((MethodCallExpr) exp).getName().toString();
        }
        return "";
    }

    class ExprComp implements Comparator<Expression> {
        @Override
        public int compare(Expression e1, Expression e2) {
            return e1.getRange().get().begin.line - e2.getRange().get().begin.line;
        }
    }

    private void putValue(Map<String, TreeSet<Expression>> varMap, String v, Expression e) {
        if (varMap.containsKey(v)) {
            varMap.get(v).add(e);
        } else {
            TreeSet<Expression> l = new TreeSet<>(new ExprComp());
            l.add(e);
            varMap.put(v, l);
        }
    }

    private String resolveType(Expression expr) {
        ResolvedType type = expr.calculateResolvedType();
        return type.describe();
    }

    private int extractPosDirectly(Node lNode) {
        Node parentNode = lNode.getParentNode().get();
        String type = "";
        if (parentNode instanceof Expression) {
            type = resolveType((Expression) parentNode);
        }
        if (!isSecurityAPI(type)) return -1;
        int pos = -1;
        NodeList<Expression> children = ((ObjectCreationExpr) parentNode).getArguments();
        pos = children.indexOf(lNode);
        return pos;
    }
}

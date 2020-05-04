package diff;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class DiffType {
    public Pattern pattern;
    public String incorrect; // change the type to list add the string
    public String correct; // change the type to list add the string
    public String callee; // change  parameter list
    public String methodType; //
    public int pos = -1;

    //TODO: record stmt in incorrect template, callee infor and methodtype
    public List<String> oldStmts = new ArrayList<>();
    public HashSet<String> stmts = new HashSet<>(); //fix patch
    public Action action;
    public String className = "";

    public DiffType() {}

    public DiffType(Pattern p, String i, String c, String callee, String methodType, int pos, HashSet<String> st, Action a) {
        this.pattern = p;
        this.incorrect = i;
        this.correct = c;
        this.callee = callee;
        this.methodType = methodType;
        this.pos = pos;
        this.stmts = st;
        this.action = a;
    }

    @Override
    public String toString() {
        return "DiffType{" +
                "pattern=" + pattern +
                ", incorrect='" + incorrect + '\'' +
                ", correct='" + correct + '\'' +
                ", callee='" + callee + '\'' +
                ", methodType='" + methodType + '\'' +
                ", pos=" + pos +
                ", action=" + action +
                ", className=" + className +
                ", stmts=" + stmts +
                '}';
    }
}

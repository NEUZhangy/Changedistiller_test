package diff;

import java.util.HashSet;

public class DiffType {
    public Pattern pattern;
    public String incorrect;
    public String correct;
    public String callee;
    public String methodType;
    public int pos = -1;
    public HashSet<String> stmts;
    public Action action;

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
                ", stmts=" + stmts +
                ", action=" + action +
                '}';
    }
}

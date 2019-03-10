package com.changedistiller.test;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ParameterPattern implements CodePattern {
    private int pos = 0;
    private String bindingType = new String();
    private Set<List<String>> correctParametersSet = new HashSet<>();
    private Set<List<String>> incorrectParameterSet = new HashSet<>();

    ParameterPattern(String str, int n){
        bindingType = str;
        this.pos = n;
    }

    public void AppendtoCSet(List<String> lstr) {
        correctParametersSet.add(lstr);
    }

    public void AppendtoISet(List<String> lstr){
        incorrectParameterSet.add(lstr);
    }

    public void AppendtoCSet(String str) {
        List<String> l = new ArrayList<>();
        l.add(str);
        correctParametersSet.add(l);
    }

    public void AppendtoISet(String str){
        List<String> l = new ArrayList<>();
        l.add(str);
        incorrectParameterSet.add(l);
    }

    public String getBingingType() {
        return bindingType;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ParameterPattern) {
            ParameterPattern rhs = (ParameterPattern) obj;
            return rhs.getBingingType().equals(this.bindingType);
        }
        return false;
    }

    public int getPos() {
        return this.pos;
    }

    public String toString() {
        return new String().format("%s_%d", this.bindingType, this.pos);
    }
}

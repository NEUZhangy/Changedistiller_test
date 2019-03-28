package com.changedistiller.test;


import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ParameterPattern implements CodePattern {
    private int pos = 0;
    private String bindingType = new String();
    private Set<String> correctParametersSet = new HashSet<>();
    private Set<String> incorrectParameterSet = new HashSet<>();

    ParameterPattern(String str, int n){
        bindingType = str;
        this.pos = n;
    }

    public Set<String> getIncorrectParameterSet() {
        return incorrectParameterSet;
    }

    public Set<String> getCorrectParametersSet() {
        return correctParametersSet;
    }

    public void AppendtoCSet(List<String> lstr) {
        correctParametersSet.addAll(lstr);
    }

    public void AppendtoISet(List<String> lstr){
        incorrectParameterSet.addAll(lstr);
    }

    public void AppendtoCSet(String str) {
        correctParametersSet.add(str);
    }

    public void AppendtoISet(String str){
        incorrectParameterSet.add(str);
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

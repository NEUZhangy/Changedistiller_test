package com.changedistiller.test;


import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ParameterPattern implements CodePattern {
    private String bindingType = new String();
    private Set<List<String>> correctParametersSet = new HashSet<>();
    private Set<List<String>> incorrectParameterSet = new HashSet<>();

    ParameterPattern(String str){
        bindingType = str;
    }

    public void AppendtoCSet(List<String> lstr) {
        correctParametersSet.add(lstr);
    }

    public void AppendtoISet(List<String> lstr){
        incorrectParameterSet.add(lstr);
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
}

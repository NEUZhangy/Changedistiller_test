package com.changedistiller.test;


import java.util.HashSet;
import java.util.Set;

public class ParameterPattern implements CodePattern {
    private String bindingType = new String();
    private Set<String> correctParametersSet = new HashSet<>();
    private Set<String> incorrectParameterSet = new HashSet<>();

    ParameterPattern(String str){
        bindingType = str;
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
}

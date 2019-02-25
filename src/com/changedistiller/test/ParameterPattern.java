package com.changedistiller.test;


import java.util.HashSet;
import java.util.Set;

public class ParameterPattern implements CodePattern {
    private String bindingType = new String();
    private Set<String> correctParametersSet = new HashSet<>();

    ParameterPattern(String str){
        bindingType = str;
    }

    public void AppendtoSet(String str) {
        correctParametersSet.add(str);
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

package com.changedistiller.test;


public class ParameterPattern implements CodePattern {
    private String bindingType = new String();

    ParameterPattern(String str){
        bindingType = str;
    }

    public String getBingingType() {
        return bindingType;
    }


}

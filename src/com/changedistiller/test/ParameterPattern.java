package com.changedistiller.test;


public class ParameterPattern implements CodePattern {
    private String bingingType = new String();

    ParameterPattern(String str){
        bingingType = str;
    }

    public String getBingingType() {
        return bingingType;
    }


}

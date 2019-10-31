package com.changedistiller.test;

import org.json.simple.JSONObject;

import java.util.HashSet;
import java.util.Set;

public class NamePattern implements CodePattern {
    private String name;
    private Set<String> correctNameSet = new HashSet<>();
    private Set<String> correctClassSet = new HashSet<>();
    private Set<String> incorrectNameSet = new HashSet<>();
    private Set<String> incorrectClassSet = new HashSet<>();

    NamePattern(){
    }

    public void AppendtoCNameSet(String str) {
        correctNameSet.add(str);
    }

    public void AppendtoCClassSet(String str) {
        correctClassSet.add(str);
    }

    public void AppendtoINameSet(String str){
        incorrectNameSet.add(str);
    }

    public void AppendtoIClassSet(String str) {
        correctClassSet.add(str);
    }

    public Set<String> getCorrectNameSet() {
        return correctNameSet;
    }

    public Set<String> getCorrectClassSet() {
        return correctClassSet;
    }

    public Set<String> getIncorrectNameSet() {
        return incorrectNameSet;
    }

    public Set<String> getIncorrectClassSet() {
        return incorrectClassSet;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
//    public String getCBingingType() {
//        return cbindingType;
//    }
//
//    public String getIbindingType() {return ibindingType; }


//    @Override
//    public boolean equals(Object obj) {
//        if (obj instanceof ParameterPattern) {
//            ParameterPattern rhs = (ParameterPattern) obj;
//            return rhs.getBingingType().equals(this.bindingType);
//        }
//        return false;
//    }


    @Override
    public JSONObject marshall() {
        return null;
    }
}

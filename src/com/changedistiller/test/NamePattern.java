package com.changedistiller.test;

import java.util.HashSet;
import java.util.Set;

public class NamePattern implements CodePattern {
   // private String cbindingType = new String();
   // private String ibindingType = new String();
    private Set<String> correctNameSet = new HashSet<>();
    private Set<String> correctclassSet = new HashSet<>();

    private Set<String> incorrectNameSet = new HashSet<>();
    private Set<String> incorrectClassSet = new HashSet<>();


    NamePattern(){
        //String istr, String cstr
        //ibindingType = istr;
        //cbindingType = cstr;
    }

    public void AppendtoCNameSet(String str) {
        correctNameSet.add(str);
    }

    public void AppendtoCClassSet(String str) {
        correctclassSet.add(str);
    }

    public void AppendtoINameSet(String str){
        incorrectNameSet.add(str);
    }

    public void AppendtoIClassSet(String str) {
        correctclassSet.add(str);
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


}

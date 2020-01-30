package Runner;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.*;
import java.util.regex.Pattern;

public class CodeCase {
    public String callee;
    public String methodType;
    public String type;
    public Long checkParameter;
    public Set<String> incorrectSet = new HashSet<>();
    public Set<String> correctSet = new HashSet<>();
    public long minNum;

    public CodeCase(JSONObject jsonObject) {
        callee = (String) jsonObject.get("callee");
        methodType = (String) jsonObject.get("MethodType");
        type = (String) jsonObject.get("Type");
        checkParameter = (Long) jsonObject.get("Check");
        incorrectSet = this.jsonArraytoSet(jsonObject.get("Incorrect"));
        correctSet = this.jsonArraytoSet(jsonObject.get("Correct"));
        if (jsonObject.get("MinNum") != null)
            this.minNum = (long) jsonObject.get("MinNum");
    }

    public List<Long> jsonArraytoList(Object o) {
        JSONArray arr = (JSONArray) o;
        List<Long> returnList = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            returnList.add((Long) arr.get(i));
        }
        return returnList;
    }

    public Set<String> jsonArraytoSet(Object o) {
        JSONArray arr = (JSONArray) o;
        Set<String> set = new HashSet<>();
        if (o == null) return null;
        for (int i = 0; i < arr.size(); i++) {
            set.add((String) arr.get(i));
        }
        return set;
    }

    public void checking(Map<String, Map<Integer, List<Object>>> classVarMap) {
        switch (this.type) {
            case "parameter":
                for (String className : classVarMap.keySet()) {
                    System.out.println(className);
                    Map<Integer, List<Object>> variables = classVarMap.get(className);
                    List<Object> ans = variables.get(this.checkParameter.intValue());
                    if (ans == null) continue;
                    if (incorrectSet == null && ans != null) {
                        System.out.println("Parameter " + this.checkParameter + " " + ans + "\n" +
                                "Suggest: " + this.correctSet);
                    } else {
                        for (Object o : ans) {
//                            if (incorrectSet.contains(o.toString())) {
//                                System.out.println("Parameter " + this.checkParameter + " " + o + "\n" +
//                                        "Suggest: " + this.correctSet);
//                            }
                            for (String p: incorrectSet) {
                                if (p.startsWith("#")) {
                                    if (p.equals("#" + o.toString())) {
                                        System.out.println("Parameter " + this.checkParameter + ": " + o + "\n" +
                                                "Suggest: " + this.correctSet);
                                    }
                                }
                                else if (Pattern.matches(p, o.toString())) {
                                    System.out.println("Parameter " + this.checkParameter + ": " + o + "\n" +
                                        "Suggest: " + this.correctSet);
                                }
                            }
                        }
                    }
                }
                break;
            case "number":
                for (String className : classVarMap.keySet()) {
                    System.out.println(className);
                    Map<Integer, List<Object>> variables = classVarMap.get(className);
                    List<Object> ans = variables.get(this.checkParameter.intValue());
                    if (ans == null) continue;
                    for (Object o : ans) {
                        try{
                            if (Integer.parseInt(o.toString()) < minNum) {
                                System.out.println("Parameter " + this.checkParameter + ": " + o + "\n" +
                                        "Suggest: " + "Should Greater Than " + minNum);
                            }
                        } catch (Exception e) {
                            continue;
                        }

                    }
                }
                break;
            case "type":
                for (String className : classVarMap.keySet()) {
                    System.out.println(className);
                    Map<Integer, List<Object>> variables = classVarMap.get(className);
                    System.out.println("Find " + this.methodType);
                    System.out.println("Suggest: Function should use: " + this.correctSet);
                }
                break;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Case Info: \n");
        sb.append("\tCallee: " + callee + '\n');
        sb.append("\tmethodType: " + methodType + '\n');
        sb.append("\tType: " + type + '\n');
        sb.append("\tCheck Parameter: " + checkParameter + '\n');
        sb.append("\tIncorrectSet: " + incorrectSet + '\n');
        sb.append("\tCorrectSet: " + correctSet + '\n');
        return sb.toString();
    }
}

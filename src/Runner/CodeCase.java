package Runner;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.*;

public class CodeCase {
    public String callee;
    public String methodType;
    public String type;
    public Long checkParameter;
    public Set<String> incorrectSet = new HashSet<>();
    public Set<String> correctSet = new HashSet<>();

    public CodeCase(JSONObject jsonObject) {
        callee = (String) jsonObject.get("callee");
        methodType = (String) jsonObject.get("MethodType");
        type = (String) jsonObject.get("Type");
        checkParameter = (Long) jsonObject.get("Check");
        incorrectSet = this.jsonArraytoSet(jsonObject.get("Incorrect"));
        correctSet = this.jsonArraytoSet(jsonObject.get("Correct"));
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
                            if (incorrectSet.contains(o.toString())) {
                                System.out.println("Parameter " + this.checkParameter + " " + o + "\n" +
                                        "Suggest: " + this.correctSet);
                            }
                        }
                    }
                }
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

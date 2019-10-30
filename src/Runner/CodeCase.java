package Runner;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Case {
    public String callee;
    public String bindingType;
    public String type;
    public List<Integer> checkParameters = new ArrayList<>();
    public Set<String> incorrectList = new HashSet<>();
    public Set<String> correctList = new HashSet<>();

    public Case(JSONObject jsonObject) {

    }


}

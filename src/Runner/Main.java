package Runner;

import com.Constant;
import com.ibm.wala.examples.slice.BackwardSlice;
import com.ibm.wala.examples.slice.BackwardSlicer2;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String []args) throws ClassHierarchyException, CancelException, IOException, ParseException {
        File file = new File("output.txt");
        PrintStream stream = new PrintStream(file);
        System.setOut(stream);
        System.setErr(stream);
        BackwardSlice backwardSlicer = new BackwardSlice();
        JSONParser jsonParser = new JSONParser();
        FileReader reader = new FileReader("src/caller.json");
        Object obj = jsonParser.parse(reader);
        JSONArray checkingCase = (JSONArray) obj;
        for (Object o: checkingCase) {
            CodeCase codeCase = new CodeCase((JSONObject)o);
            System.out.println("\n=====" + codeCase.type + " " + codeCase.methodType + " " + codeCase.callee + "=====");
            backwardSlicer.run(Constant.FILEPATH, codeCase.callee, codeCase.methodType);
            Map<String, Map<Integer, List<Object>>> varMap = backwardSlicer.getClassVarMap();
            Map<String, HashMap<Integer, List<Integer>>> classLineNums = backwardSlicer.getClassParamsLinesNumsMap();
//            System.err.println(classLineNums);
            codeCase.checking(varMap);
        }
    }
}

import com.Constant;
import com.ibm.wala.examples.slice.BackwardSlicer2;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Main {

    public static void main(String []args) throws ClassHierarchyException, CancelException, IOException {
        BackwardSlicer2 backwardSlicer = new BackwardSlicer2();


        String callee = "<init>";
        String functionType = "SecretKeySpec";

        backwardSlicer.run(Constant.FILEPATH, callee, functionType);
        Map<String, Map<Integer, List<Object>>> classVarMap = backwardSlicer.getClassVarMap();
        System.out.println(classVarMap);


    }


}

import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import org.junit.Test;

import java.io.IOException;

public class DetectionTest {

    @Test
    public void run() throws CancelException, IOException, ClassHierarchyException {
        Detection detection = new Detection();
        detection.runSize();
       // detection.run();
     //  detection.runParam();
//        detection.runStatic();


    }
}
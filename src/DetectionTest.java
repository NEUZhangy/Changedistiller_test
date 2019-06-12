import com.ibm.wala.util.CancelException;
import org.junit.Test;

import java.io.IOException;

public class DetectionTest {

    @Test
    public void run() throws CancelException, IOException {
        Detection detection = new Detection();
        detection.run();
    }
}
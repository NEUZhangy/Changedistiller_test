package com.ibm.wala.examples.slice;

import com.Constant;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import org.junit.Test;

import java.io.IOException;

public class BackwardSlicerTest {

    @Test
    public void run() throws ClassHierarchyException, CancelException, IOException {
        String path = Constant.FILEPATH;
        String caller = "main";

        String mainClass ="Lorg/cryptoapi/bench/predictablecryptographickey/PredictableCryptographicKeyBBCase1";
        String callee = "setSeed";
        String functionType = "SecureRandom";

//        String mainClass = "Lorg/cryptoapi/bench/brokencrypto/BrokenCryptoABICase8";
//        String callee = "getInstance";
//        String functionType = "Cipher";

//        BackwardSlicer backwardSlicer = new BackwardSlicer();
//        backwardSlicer.run(path, mainClass, callee, caller, functionType);

        BackwardSlice backwardSlicer = new BackwardSlice(path);
        backwardSlicer.run(path, callee, functionType);


    }
}

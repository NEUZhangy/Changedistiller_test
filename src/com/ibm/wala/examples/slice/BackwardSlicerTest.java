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
        String mainClass ="Lorg/cryptoapi/bench/predictablecryptographickey/PredictableCryptographicKeyBBCase1";
//        String mainClass = "Lorg/cryptoapi/bench/brokencrypto/BrokenCryptoABICase2";
        String caller = "main";
        String callee = "<init>";
        String functionType = "SecretKeySpec";
        BackwardSlicer backwardSlicer = new BackwardSlicer();
        backwardSlicer.run(path, mainClass, callee, caller, functionType);


    }
}
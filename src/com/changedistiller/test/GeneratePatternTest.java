package com.changedistiller.test;

import junit.framework.TestCase;

import java.util.List;

//import static org.junit.jupiter.api.Assertions.*;
//
public class GeneratePatternTest extends TestCase {

    public void testdivideArgument() {
        GeneratePattern gp = new GeneratePattern();
        List<String> arrStr = gp.divideArgument("AES/CBC/NoPadding");
        for (String str: arrStr) {
            System.out.println(str);
        }
    }
}
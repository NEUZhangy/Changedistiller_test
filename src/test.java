import template.StringLiterals;

import javax.crypto.Cipher;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class test {

    public void main(String[] args) throws Exception{
        StringLiterals incorrects = new StringLiterals("DES", "MD5");
        StringLiterals corrects = new StringLiterals("b", "a");
        Cipher c = Cipher.getInstance(incorrects.getAString());
        int a = 1;
    }

}
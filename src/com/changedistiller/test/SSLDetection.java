package com.changedistiller.test;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;

public class SSLDetection {

    Map<String, String> paramMap = new HashMap<String, String>();
    String fixPatch = null;
    public void detect(CompilationUnit cu, String filename) throws FileNotFoundException {
        PrintStream stream = new PrintStream(new FileOutputStream("SSLoutput-20210614.txt", true));
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodDeclaration node) {
                if (node.getName().toString().contains("checkClientTrusted") ||
                node.getName().toString().contains("checkServerTrusted")){
                   if (node.getBody().statements().size() == 0 || node.thrownExceptionTypes().size() == 0) {
                       //get parameter and save to a map
                       for(int i = 0; i<node.parameters().size(); i++) {
                            paramMap.put("$v_" + i + "$" , node.parameters().get(i).toString());
                       }
                       String name =  node.getName().toString();

                       fixPatch = readTemp(name);

                       stream.println(filename + ": " + node.getName().toString() + " insecure");

                   }
                }
                if (node.getName().toString().contains("verify")) {
                    if (node.getBody().statements().size() == 1 || node.thrownExceptionTypes().size() == 0) {
                        stream.println(filename + ": " + node.getName().toString() + " insecure");
                        for(int i = 0; i< node.parameters().size(); i++) {
                            paramMap.put("$v_" + i + "$" , node.parameters().get(i).toString());
                        }
                        String name =  node.getName().toString();
                        fixPatch = readTemp(name);
                        stream.println(filename + ": " + node.getName().toString() + " insecure");

                    }
                }

                return super.visit(node);
            }
        });

    }

    private String readTemp(String name) {
        String fix = null;
        try {
            Scanner scanner;
            if(name.contains("checkClientTrusted")) {
                scanner = new Scanner(new File("src/SSLtemplate/checkclienttrust.template"));
            } else if (name.contains("checkServerTrusted")) {
                scanner = new Scanner(new File("src/SSLtemplate/checkservertrust.template"));
            } else {
                scanner = new Scanner(new File("src/SSLtemplate/hostnameverify.template"));
            }

            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                line = replaceVar(line);
                fix += line;
                System.out.println(line);
            }

            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return fix;
    }

    private String replaceVar(String stmtwithvar) {
        String s = stmtwithvar;
        for (Map.Entry<String, String> entry : paramMap.entrySet()) {
            String searchStr = entry.getKey(); //key?
//            searchStr = searchStr.substring(0, searchStr.length() - 1) + "\\$";
            s = s.replace(searchStr, entry.getValue().split(" ")[1]);
        }
        return s;
    }

}

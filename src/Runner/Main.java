package Runner;

import com.Constant;
import com.ibm.wala.examples.slice.InterRetrive;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.util.CancelException;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args)
            throws ClassHierarchyException, CancelException, IOException, ParseException {
        boolean isRunApache = true;
        PrintStream stream = new PrintStream(new FileOutputStream("output.txt", true));
        System.setOut(stream);
        System.setErr(stream);
        //        Files.write(Paths.get("result.txt"), "".getBytes());
        JSONParser jsonParser = new JSONParser();
        FileReader reader = new FileReader("src/caller.json");
        Object obj = jsonParser.parse(reader);
        JSONArray checkingCase = (JSONArray) obj;
        String projectSource = "";
        if (isRunApache) {
            //            File folder = new
            // File("C:\\Users\\Ying\\Desktop\\experiment\\java-security-test-jar");
            //            File[] files = folder.listFiles();
            //            ExecutorService executorService = Executors.newFixedThreadPool(100);
            //            for (File f: files) {
            //                System.out.println(f.getCanonicalPath());
            //                long caseStartTime = System.nanoTime();
            //                FutureTask<Void> future = new FutureTask<>(new Callable<Void>() {
            //                    @Override
            //                    public Void call() throws Exception {
            //                        new Main().runCaseChecking(f.getCanonicalPath(),
            // checkingCase);
            //                        return null;
            //                    }
            //                });
            //                executorService.execute(future);
            //                try {
            //                    future.get(120, TimeUnit.SECONDS);
            //                } catch (Exception e) {
            //                    System.out.println("Cancel the task: " + future.cancel(true));
            //                    e.printStackTrace();
            //                    System.out.println("TIMEOUT: " + f.getCanonicalPath() + "\n");
            //                }
            //                break;
            long caseStartTime = System.nanoTime();
            System.out.println(args[0]);
            if (args.length > 1) {
               projectSource = args[1];
            }
            new Main().runCaseChecking(args[0], checkingCase, projectSource);
            System.out.println(
                    "\nDURATION: "
                            + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - caseStartTime)
                            + "ms\n");
        } else {
            new Main().runCaseChecking(Constant.FILEPATH, checkingCase, projectSource);
        }
    }

    public void runCaseChecking(String filePath, JSONArray checkingCase, String projectSource)
            throws IOException, ClassHierarchyException, CallGraphBuilderCancelException {
        int count = 0;
        Map<Integer, String> caseName = new HashMap<>();
        Map<Integer, Integer> caseCount = new HashMap<>();
        InterRetrive backwardSlicer = new InterRetrive(filePath);
        for (Object o : checkingCase) {
            CodeCase codeCase = new CodeCase((JSONObject) o);
            codeCase.jarPath = filePath;
            if (projectSource != "")
                codeCase.projectSource = projectSource;
            System.out.println(
                    "\n====="
                            + codeCase.type
                            + " "
                            + codeCase.methodType
                            + " "
                            + codeCase.callee
                            + "=====");
            // running apache case
            try {
                backwardSlicer.start(filePath, codeCase.callee, codeCase.methodType);
                Map<String, Map<Integer, List<Object>>> varMap = backwardSlicer.getClassVarMap();
                //                Map<String, HashMap<Integer, List<Integer>>> classLineNums =
                // backwardSlicer.getClassParamsLinesNumsMap();
                //            System.err.println(classLineNums);
                caseCount.put(count, codeCase.checking(varMap));
                caseName.put(count, codeCase.methodType + " " + codeCase.callee);
            } catch (Throwable e) {
                continue;
            }
            count++;
        }
        // output the summary
        writetoFile("############################################################\n");
        writetoFile(filePath + "\n");
        for (Map.Entry<Integer, Integer> e : caseCount.entrySet()) {
            //            System.out.printf("Rule %s: %d\n", caseName.get(e.getKey()),
            // e.getValue());
            writetoFile(String.format("Rule %s: %d\n", caseName.get(e.getKey()), e.getValue()));
        }
    }

    public void writetoFile(String str) throws IOException {
        Files.write(Paths.get("result.txt"), str.getBytes(), StandardOpenOption.APPEND);
    }
}

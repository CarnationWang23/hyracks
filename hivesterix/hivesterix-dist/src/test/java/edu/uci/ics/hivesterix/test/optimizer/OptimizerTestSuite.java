package edu.uci.ics.hivesterix.test.optimizer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestResult;
import edu.uci.ics.hivesterix.test.base.AbstractTestSuiteClass;

public class OptimizerTestSuite extends AbstractTestSuiteClass {

    private static final String PATH_TO_QUERIES = "src/test/resources/optimizerts/queries/";
    private static final String PATH_TO_RESULTS = "src/test/resources/optimizerts/results/";
    private static final String PATH_TO_IGNORES = "src/test/resources/optimizerts/ignore.txt";
    private static final String PATH_TO_HIVE_CONF = "src/test/resources/optimizerts/hive/conf/hive-default.xml";

    private static final String FILE_EXTENSION_OF_RESULTS = "plan";

    public static Test suite() throws UnsupportedEncodingException, FileNotFoundException, IOException {
        List<String> ignores = getIgnoreList(PATH_TO_IGNORES);
        File testData = new File(PATH_TO_QUERIES);
        File[] queries = testData.listFiles();
        OptimizerTestSuite testSuite = new OptimizerTestSuite();

        // set hdfs and hyracks cluster, and load test data to hdfs
        try {
            testSuite.setup(PATH_TO_HIVE_CONF);
            testSuite.loadData();
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException(e.getMessage());
        }

        for (File qFile : queries) {
            if (isIgnored(qFile.getName(), ignores))
                continue;

            if (qFile.isFile()) {
                String resultFileName = hiveExtToResExt(qFile.getName());
                File rFile = new File(PATH_TO_RESULTS + resultFileName);
                testSuite.addTest(new OptimizerTestCase(qFile, rFile));
            }
        }
        return testSuite;
    }

    private static String hiveExtToResExt(String fname) {
        int dot = fname.lastIndexOf('.');
        return fname.substring(0, dot + 1) + FILE_EXTENSION_OF_RESULTS;
    }
    
    /**
     * Runs the tests and collects their result in a TestResult.
     */
    @Override
    public void run(TestResult result) {

        int testCount = countTestCases();
        for (int i = 0; i < testCount; i++) {
            Test each = this.testAt(i);
            if (result.shouldStop())
                break;
            runTest(each, result);
        }

        // cleanup hdfs and hyracks cluster
        try {
            cleanup();
        } catch (Exception e) {
            e.printStackTrace();
            throw new IllegalStateException(e.getMessage());
        }
    }

}

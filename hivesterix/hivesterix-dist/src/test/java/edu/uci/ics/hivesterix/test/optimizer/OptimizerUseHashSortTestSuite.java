package edu.uci.ics.hivesterix.test.optimizer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;

import junit.framework.Test;
import edu.uci.ics.hivesterix.test.base.AbstractTestSuiteClass;

public class OptimizerUseHashSortTestSuite extends AbstractTestSuiteClass {

    private static final String PATH_TO_QUERIES = "src/test/resources/optimizerts/queries/";
    private static final String PATH_TO_RESULTS = "src/test/resources/optimizerts/results-hs/";
    private static final String PATH_TO_IGNORES = "src/test/resources/optimizerts/ignore.txt";
    private static final String PATH_TO_HIVE_CONF = "src/test/resources/optimizerts/hive/conf/hive-default-hs.xml";

    private static final String FILE_EXTENSION_OF_RESULTS = "plan";

    public static Test suite() throws UnsupportedEncodingException, FileNotFoundException, IOException {
        List<String> ignores = getIgnoreList(PATH_TO_IGNORES);
        File testData = new File(PATH_TO_QUERIES);
        File[] queries = testData.listFiles();
        OptimizerUseHashSortTestSuite testSuite = new OptimizerUseHashSortTestSuite();

        // set hdfs and hyracks cluster, and load test data to hdfs
        try {
            testSuite.setup();
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

}

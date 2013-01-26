/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uci.ics.hyracks.imru.example.utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.logging.Handler;
import java.util.logging.Logger;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import edu.uci.ics.hyracks.api.client.IHyracksClientConnection;
import edu.uci.ics.hyracks.api.job.JobFlag;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.api.job.JobStatus;
import edu.uci.ics.hyracks.control.cc.ClusterControllerService;
import edu.uci.ics.hyracks.control.common.controllers.CCConfig;
import edu.uci.ics.hyracks.control.common.controllers.NCConfig;
import edu.uci.ics.hyracks.control.nc.NodeControllerService;
import edu.uci.ics.hyracks.imru.api.IModel;
import edu.uci.ics.hyracks.imru.api2.IIMRUJobSpecificationImpl;
import edu.uci.ics.hyracks.imru.api2.IIMRUJob2;
import edu.uci.ics.hyracks.imru.api2.IMRUJobControl;
import edu.uci.ics.hyracks.imru.api2.IIMRUJob;

/**
 * This class wraps IMRU common functions.
 * Example usage: <blockquote>
 * 
 * <pre>
 * Client&lt;Model, MapReduceResult&gt; client = new Client&lt;Model, MapReduceResult&gt;(args);
 * //start a in-process cluster for debugging 
 * //client.startClusterAndNodes();  
 * client.connect();
 * client.uploadApp();
 * IMRUJob job = new IMRUJob();
 * JobStatus status = client.run(job);
 * Model finalModel = client.getModel();
 * </pre>
 * 
 * </blockquote>
 * 
 * @author wangrui
 * @param <Model>
 *            IMRU model which will be used in map() and updated in update()
 * @param <T>
 *            Object which is generated by map(), aggregated in reduce() and
 *            used in update()
 */
public class Client<Model extends IModel> {
    public static class Options {
        @Option(name = "-debug", usage = "Start cluster controller and node controller in this process for debugging")
        public boolean debug;

        @Option(name = "-disable-logging", usage = "Disable logging. So console output can be seen when debugging")
        public boolean disableLogging;

        @Option(name = "-host", usage = "Hyracks Cluster Controller Host name", required = true)
        public String host;

        @Option(name = "-port", usage = "Hyracks Cluster Controller Port (default: 1099)")
        public int port = 3099;

        @Option(name = "-clusterport", usage = "Hyracks Cluster Controller Port (default: 3099)")
        public int clusterPort = 1099;

        @Option(name = "-app", usage = "Hyracks Application name")
        public String app = "imru-examples";

        @Option(name = "-hadoop-conf", usage = "Path to Hadoop configuration", required = true)
        public String hadoopConfPath;

        @Option(name = "-cluster-conf", usage = "Path to Hyracks cluster configuration")
        public String clusterConfPath = "conf/cluster.conf";

        @Option(name = "-temp-path", usage = "HDFS path to hold temporary files", required = true)
        public String tempPath;

        @Option(name = "-abondon-intermediate-models", usage = "Don't save intermediate models to the temp directory")
        public boolean abondonIntermediateModels;

        @Option(name = "-model-file-name", usage = "Name of the model file")
        public String modelFileNameHDFS;

        @Option(name = "-using-existing-model", usage = "Use existing model if possible")
        public boolean useExistingModels;

        @Option(name = "-example-paths", usage = "HDFS path to hold input data")
        public String examplePaths = "/input/data.txt";

        @Option(name = "-agg-tree-type", usage = "The aggregation tree type (none, rack, nary, or generic)", required = true)
        public String aggTreeType;

        @Option(name = "-agg-count", usage = "The number of aggregators to use, if using an aggregation tree")
        public int aggCount = -1;

        @Option(name = "-fan-in", usage = "The fan-in, if using an nary aggregation tree")
        public int fanIn = -1;

        @Option(name = "-model-file", usage = "Local file to write the final weights to")
        public String modelFilename;

        //        @Option(name = "-num-rounds", usage = "The number of iterations to perform")
        //        public int numRounds = 5;
    }

    public static final int FRAME_SIZE = 65536;

    private ClusterControllerService cc;
    private NodeControllerService nc1;
    private NodeControllerService nc2;
    private IHyracksClientConnection hcc;

    public IMRUJobControl<Model> control;
    public Options options = new Options();
    Configuration conf;
    private static boolean alreadyStartedDebug = false;
    private static HashSet<String> uploadedApps = new HashSet<String>();

    /**
     * Create a client object using a list of arguments
     * 
     * @param args
     * @throws CmdLineException
     */
    public Client(String[] args) throws CmdLineException {
        CmdLineParser parser = new CmdLineParser(options);
        parser.parseArgument(args);
    }

    /**
     * Return local host name
     * 
     * @return
     * @throws Exception
     */
    public static String getLocalHostName() throws Exception {
        return java.net.InetAddress.getLocalHost().getHostName();
    }

    /**
     * Return same ip as pregelix/pregelix-dist/target/appassembler/bin/getip.sh
     * 
     * @return
     * @throws Exception
     */
    public static String getLocalIp() throws Exception {
        String ip = "127.0.0.1";
        NetworkInterface netint = NetworkInterface.getByName("eth0");
        if (netint != null) {
            Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
            for (InetAddress inetAddress : Collections.list(inetAddresses)) {
                byte[] addr = inetAddress.getAddress();
                if (addr != null && addr.length == 4)
                    ip = inetAddress.getHostAddress();
            }
        }
        return ip;
    }

    /**
     * Generate cluster config file, required by IMRU
     * 
     * @param file
     *            the config file to be written
     * @param args
     *            a list of ip and node names
     * @throws IOException
     */
    public static void generateClusterConfig(File file, String... args) throws IOException {
        PrintStream ps = new PrintStream(file);
        for (int i = 0; i < args.length / 2; i++)
            ps.println(args[i * 2] + " " + args[i * 2 + 1]);
        ps.close();
    }

    /**
     * connect to the cluster controller
     * 
     * @throws Exception
     */
    public void connect() throws Exception {
        this.control = new IMRUJobControl<Model>();
        control.saveIntermediateModels = !options.abondonIntermediateModels;
        control.modelFileName = options.modelFileNameHDFS;
        control.useExistingModels = options.useExistingModels;
        control.connect(options.host, options.port, options.hadoopConfPath, options.clusterConfPath);
        hcc = control.hcc;
        conf = control.conf;
        // set aggregation type
        if (options.aggTreeType.equals("none")) {
            control.selectNoAggregation(options.examplePaths);
        } else if (options.aggTreeType.equals("generic")) {
            control.selectGenericAggregation(options.examplePaths, options.aggCount);
        } else if (options.aggTreeType.equals("nary")) {
            control.selectNAryAggregation(options.examplePaths, options.fanIn);
        } else {
            throw new IllegalArgumentException("Invalid aggregation tree type");
        }
        // hyracks connection
    }

    /**
     * run IMRU job
     * 
     * @throws Exception
     */
    public <Data extends Serializable, T extends Serializable> JobStatus run(IIMRUJob<Model, Data, T> job)
            throws Exception {
        return control.run(job, options.tempPath, options.app);
    }

    /**
     * run IMRU job using low level interface
     * 
     * @throws Exception
     */
    public JobStatus run(IIMRUJob2<Model> job) throws Exception {
        return control.run(job, options.tempPath, options.app);
    }

    /**
     * run IMRU job using callback interface
     * 
     * @throws Exception
     */
    public JobStatus run(IIMRUJobSpecificationImpl<Model> job, Model initialModel) throws Exception {
        return control.run(job, initialModel, options.tempPath, options.app);
    }

    /**
     * @return a handle to HDFS
     * @throws IOException
     */
    public FileSystem getHDFS() throws IOException {
        return FileSystem.get(conf);
    }

    /**
     * Clear HDFS temp directory which holds intermediate models
     * 
     * @throws Exception
     */
    public void clearTempDirectory() throws Exception {
        FileSystem dfs = getHDFS();
        // remove old intermediate models
        if (dfs.listStatus(new Path(options.tempPath)) != null)
            for (FileStatus f : dfs.listStatus(new Path(options.tempPath)))
                dfs.delete(f.getPath());
        dfs.close();
    }

    /**
     * start local cluster controller and two node controller for debugging
     * purpose
     * 
     * @throws Exception
     */
    public void startClusterAndNodes() throws Exception {
        startCC(options.host, options.clusterPort, options.port);
        startNC1("nc1", options.host, options.clusterPort);
        startNC2("nc2", options.host, options.clusterPort);
    }

    /**
     * Start a cluster controller
     * 
     * @param host
     * @param clusterNetPort
     * @param clientNetPort
     * @throws Exception
     */
    public void startCC(String host, int clusterNetPort, int clientNetPort) throws Exception {
        CCConfig ccConfig = new CCConfig();
        ccConfig.clientNetIpAddress = host;
        ccConfig.clusterNetIpAddress = host;
        ccConfig.clusterNetPort = clusterNetPort;
        ccConfig.clientNetPort = clientNetPort;
        ccConfig.defaultMaxJobAttempts = 0;
        ccConfig.jobHistorySize = 10;

        // cluster controller
        cc = new ClusterControllerService(ccConfig);
        cc.start();
    }

    /**
     * Start the first node controller
     * 
     * @param NC1_ID
     * @param host
     * @param clusterNetPort
     * @throws Exception
     */
    public void startNC1(String NC1_ID, String host, int clusterNetPort) throws Exception {
        NCConfig ncConfig1 = new NCConfig();
        ncConfig1.ccHost = host;
        ncConfig1.clusterNetIPAddress = host;
        ncConfig1.ccPort = clusterNetPort;
        ncConfig1.dataIPAddress = "127.0.0.1";
        ncConfig1.nodeId = NC1_ID;
        nc1 = new NodeControllerService(ncConfig1);
        nc1.start();
    }

    /**
     * Start the second node controller
     * 
     * @param NC2_ID
     * @param host
     * @param clusterNetPort
     * @throws Exception
     */
    public void startNC2(String NC2_ID, String host, int clusterNetPort) throws Exception {
        NCConfig ncConfig2 = new NCConfig();
        ncConfig2.ccHost = host;
        ncConfig2.clusterNetIPAddress = host;
        ncConfig2.ccPort = clusterNetPort;
        ncConfig2.dataIPAddress = "127.0.0.1";
        ncConfig2.nodeId = NC2_ID;
        nc2 = new NodeControllerService(ncConfig2);
        nc2.start();

        // ClusterConfig
        // .setClusterPropertiesPath("imru/imru-core/src/main/resources/conf/cluster.properties");
        // ClusterConfig
        // .setStorePath("imru/imru-core/src/main/resources/conf/stores.properties");
        // ClusterConfig.loadClusterConfig(CC_HOST,
        // TEST_HYRACKS_CC_CLIENT_PORT);
    }

    /**
     * disable logs
     * 
     * @throws Exception
     */
    public static void disableLogging() throws Exception {
        Logger globalLogger = Logger.getLogger("");
        Handler[] handlers = globalLogger.getHandlers();
        for (Handler handler : handlers)
            globalLogger.removeHandler(handler);
    }

    /**
     * Remove the application
     * 
     * @param hyracksAppName
     * @throws Exception
     */
    public void destroyApp(String hyracksAppName) throws Exception {
        hcc.destroyApplication(hyracksAppName);
    }

    /**
     * Stop cluster controller and node controllers
     * 
     * @throws Exception
     */
    public void deinit() throws Exception {
        nc2.stop();
        nc1.stop();
        cc.stop();
    }

    /**
     * Run an already uploaded job
     * 
     * @param spec
     * @param appName
     * @throws Exception
     */
    public void runJob(JobSpecification spec, String appName) throws Exception {
        spec.setFrameSize(FRAME_SIZE);
        JobId jobId = hcc.startJob(appName, spec, EnumSet.of(JobFlag.PROFILE_RUNTIME));
        hcc.waitForCompletion(jobId);
    }

    /**
     * Write raw data to a local file
     * 
     * @param file
     * @param bs
     * @throws IOException
     */
    public void writeLocalFile(File file, byte[] bs) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        out.write(bs);
        out.close();
    }

    /**
     * Copy a local file to HDFS
     * 
     * @param localPath
     * @param hdfsPath
     * @throws IOException
     */
    public void copyFromLocalToHDFS(String localPath, String hdfsPath) throws IOException {
        FileSystem dfs = getHDFS();
        dfs.mkdirs(new Path(hdfsPath).getParent());
        System.out.println("copy " + localPath + " to " + hdfsPath);
        dfs.copyFromLocalFile(new Path(localPath), new Path(hdfsPath));
        dfs.close();
    }

    /**
     * Create a HAR file contains jars specified in .classpath and uploaded to
     * hyracks cluster
     * 
     * @throws Exception
     */
    public void uploadApp() throws Exception {
        File harFile = File.createTempFile("imru_app", ".zip");
        FileOutputStream out = new FileOutputStream(harFile);
        CreateHar.createHar(harFile);
        //        System.out.println("Upload "+ appName+" "+ harFile.getAbsolutePath());
        out.close();
        try {
            hcc.createApplication(options.app, harFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //        harFile.delete();
    }

    /**
     * @return The most recent model.
     */
    public Model getModel() {
        return control.getModel();
    }

    /**
     * start local cluster controller and two node controller for debugging
     * purpose
     * 
     * @throws Exception
     */
    public static void startClusterAndNodes(String[] args) throws Exception {
        Client clent = new Client<IModel>(args);
        clent.startClusterAndNodes();
    }

    public void init() throws Exception {
        // disable logs
        if (options.disableLogging)
            Client.disableLogging();

        // start local cluster controller and two node controller
        // for debugging purpose
        if (options.debug && !alreadyStartedDebug) {
            alreadyStartedDebug = true;
            startClusterAndNodes();
        }

        // connect to the cluster controller
        connect();

        if (!uploadedApps.contains(options.app)) {
            // create the application in local cluster
            uploadApp();
            uploadedApps.add(options.app);
        }
    }

    /**
     * run job
     * 
     * @throws Exception
     */
    public static <M extends IModel, D extends Serializable, R extends Serializable> M run(IIMRUJob<M, D, R> job,
            String[] args) throws Exception {
        return run(job, args, null);
    }

    /**
     * run job
     * 
     * @throws Exception
     */
    public static <M extends IModel, D extends Serializable, R extends Serializable> M run(IIMRUJob<M, D, R> job,
            String[] args, String overrideAppName) throws Exception {
        // create a client object, which handles everything
        Client<M> client = new Client<M>(args);

        if (overrideAppName != null)
            client.options.app = overrideAppName;
        client.init();

        // run job
        JobStatus status = client.run(job);
        if (status == JobStatus.FAILURE) {
            System.err.println("Job failed; see CC and NC logs");
            System.exit(-1);
        }
        // System.out.println("Terminated after "
        // + client.control.getIterationCount() + " iterations");

        return client.getModel();
    }
}

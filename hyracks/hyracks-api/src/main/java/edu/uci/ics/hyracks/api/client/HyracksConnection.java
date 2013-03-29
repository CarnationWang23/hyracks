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
package edu.uci.ics.hyracks.api.client;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import edu.uci.ics.hyracks.api.client.impl.JobSpecificationActivityClusterGraphGeneratorFactory;
import edu.uci.ics.hyracks.api.comm.NetworkAddress;
import edu.uci.ics.hyracks.api.exceptions.HyracksException;
import edu.uci.ics.hyracks.api.job.IActivityClusterGraphGeneratorFactory;
import edu.uci.ics.hyracks.api.job.JobFlag;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.api.job.JobStatus;
import edu.uci.ics.hyracks.api.topology.ClusterTopology;
import edu.uci.ics.hyracks.api.util.JavaSerializationUtils;
import edu.uci.ics.hyracks.ipc.api.IIPCHandle;
import edu.uci.ics.hyracks.ipc.api.RPCInterface;
import edu.uci.ics.hyracks.ipc.impl.IPCSystem;
import edu.uci.ics.hyracks.ipc.impl.JavaSerializationBasedPayloadSerializerDeserializer;

/**
 * Connection Class used by a Hyracks Client to interact with a Hyracks Cluster
 * Controller.
 * 
 * @author vinayakb
 */
public final class HyracksConnection implements IHyracksClientConnection {
    private final String ccHost;

    private final IPCSystem ipc;

    private final IHyracksClientInterface hci;

    private final ClusterControllerInfo ccInfo;

    /**
     * Constructor to create a connection to the Hyracks Cluster Controller.
     * 
     * @param ccHost
     *            Host name (or IP Address) where the Cluster Controller can be
     *            reached.
     * @param ccPort
     *            Port to reach the Hyracks Cluster Controller at the specified
     *            host name.
     * @throws Exception
     */
    public HyracksConnection(String ccHost, int ccPort) throws Exception {
        this.ccHost = ccHost;
        RPCInterface rpci = new RPCInterface();
        ipc = new IPCSystem(new InetSocketAddress(0), rpci, new JavaSerializationBasedPayloadSerializerDeserializer());
        ipc.start();
        IIPCHandle ccIpchandle = ipc.getHandle(new InetSocketAddress(ccHost, ccPort));
        this.hci = new HyracksClientInterfaceRemoteProxy(ccIpchandle, rpci);
        ccInfo = hci.getClusterControllerInfo();
    }

    @Override
    public JobStatus getJobStatus(JobId jobId) throws Exception {
        return hci.getJobStatus(jobId);
    }

    @Override
    public JobId startJob(JobSpecification jobSpec) throws Exception {
        return startJob(jobSpec, EnumSet.noneOf(JobFlag.class));
    }

    @Override
    public JobId startJob(JobSpecification jobSpec, EnumSet<JobFlag> jobFlags) throws Exception {
        JobSpecificationActivityClusterGraphGeneratorFactory jsacggf = new JobSpecificationActivityClusterGraphGeneratorFactory(
                jobSpec);
        return startJob(jsacggf, jobFlags);
    }

    public JobId startJob(IActivityClusterGraphGeneratorFactory acggf, EnumSet<JobFlag> jobFlags) throws Exception {
        return hci.startJob(JavaSerializationUtils.serialize(acggf), jobFlags);
    }

    public NetworkAddress getDatasetDirectoryServiceInfo() throws Exception {
        return hci.getDatasetDirectoryServiceInfo();
    }

    @Override
    public void waitForCompletion(JobId jobId) throws Exception {
        hci.waitForCompletion(jobId);
    }

    @Override
    public Map<String, NodeControllerInfo> getNodeControllerInfos() throws Exception {
        return hci.getNodeControllersInfo();
    }

    @Override
    public ClusterTopology getClusterTopology() throws Exception {
        return hci.getClusterTopology();
    }

    @Override
    public void deployBinary(List<String> jars) throws Exception {
        List<URL> binaryURLs = new ArrayList<URL>();
        if (jars != null && jars.size() > 0) {
            HttpClient hc = new DefaultHttpClient();
            for (String jar : jars) {
                String url = "http://" + ccHost + ":" + ccInfo.getWebPort() + "/applications/" + jar;
                HttpPut put = new HttpPut(url);
                put.setEntity(new FileEntity(new File(jar), "application/octet-stream"));
                HttpResponse response = hc.execute(put);
                if (response.getStatusLine().getStatusCode() != 200) {
                    throw new HyracksException(response.getStatusLine().toString());
                }
                binaryURLs.add(new URL(url));
            }
        }
        hci.deployBinary(binaryURLs);
    }
}
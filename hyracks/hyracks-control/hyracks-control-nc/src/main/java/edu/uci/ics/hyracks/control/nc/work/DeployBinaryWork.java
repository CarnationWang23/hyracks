/*
 * Copyright 2009-2013 by The Regents of the University of California
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

package edu.uci.ics.hyracks.control.nc.work;

import java.net.URL;
import java.util.List;

import edu.uci.ics.hyracks.api.deployment.DeploymentId;
import edu.uci.ics.hyracks.control.common.base.IClusterController;
import edu.uci.ics.hyracks.control.common.deployment.DeploymentStatus;
import edu.uci.ics.hyracks.control.common.deployment.DeploymentUtils;
import edu.uci.ics.hyracks.control.common.work.AbstractWork;
import edu.uci.ics.hyracks.control.nc.NodeControllerService;

public class DeployBinaryWork extends AbstractWork {

    private DeploymentId deploymentId;
    private NodeControllerService ncs;
    private List<URL> binaryURLs;

    public DeployBinaryWork(NodeControllerService ncs, DeploymentId deploymentId, List<URL> binaryURLs) {
        this.deploymentId = deploymentId;
        this.ncs = ncs;
        this.binaryURLs = binaryURLs;
    }

    @Override
    public void run() {
        DeploymentStatus status;
        try {
            DeploymentUtils.deploy(deploymentId, binaryURLs, ncs.getApplicationContext()
                    .getJobSerializerDeserializerContainer(), ncs.getServerContext(), true);
            status = DeploymentStatus.SUCCEED;
        } catch (Exception e) {
            status = DeploymentStatus.FAIL;
            e.printStackTrace();
        }
        try {
            IClusterController ccs = ncs.getClusterController();
            ccs.notifyDeployBinary(deploymentId, ncs.getId(), status);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}

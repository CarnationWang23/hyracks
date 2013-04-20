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

package edu.uci.ics.hyracks.control.cc.work;

import edu.uci.ics.hyracks.api.deployment.DeploymentId;
import edu.uci.ics.hyracks.control.cc.ClusterControllerService;
import edu.uci.ics.hyracks.control.common.deployment.DeploymentRun;
import edu.uci.ics.hyracks.control.common.deployment.DeploymentStatus;
import edu.uci.ics.hyracks.control.common.work.AbstractWork;

public class NotifyDeployBinaryWork extends AbstractWork {

    private final ClusterControllerService ccs;
    private final String nodeId;
    private final DeploymentId deploymentId;
    private DeploymentStatus deploymentStatus;

    public NotifyDeployBinaryWork(ClusterControllerService ccs, DeploymentId deploymentId, String nodeId,
            DeploymentStatus deploymentStatus) {
        this.ccs = ccs;
        this.nodeId = nodeId;
        this.deploymentId = deploymentId;
        this.deploymentStatus = deploymentStatus;

    }

    @Override
    public void run() {
        // notify that one NC is deployed
        DeploymentRun dRun = ccs.getDeploymentRun(deploymentId);
        dRun.notifyDeploymentStatus(nodeId, deploymentStatus);
    }

}

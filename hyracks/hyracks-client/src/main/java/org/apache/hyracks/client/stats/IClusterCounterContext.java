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

package org.apache.hyracks.client.stats;

import org.apache.hyracks.api.job.profiling.counters.ICounter;
import org.apache.hyracks.api.job.profiling.counters.ICounterContext;

public interface IClusterCounterContext extends ICounterContext {

    public ICounter getCounter(String machineName, String counterName, boolean create);
    
}

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

package edu.uci.ics.hyracks.storage.am.lsm.invertedindex.impls;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTree;
import edu.uci.ics.hyracks.storage.am.lsm.common.api.IVirtualBufferCache;
import edu.uci.ics.hyracks.storage.am.lsm.common.impls.AbstractMutableLSMComponent;
import edu.uci.ics.hyracks.storage.am.lsm.invertedindex.api.IInvertedIndex;

public class LSMInvertedIndexMutableComponent extends AbstractMutableLSMComponent {

    private final IInvertedIndex invIndex;
    private final BTree deletedKeysBTree;
    private final IVirtualBufferCache vbc;

    public LSMInvertedIndexMutableComponent(IInvertedIndex invIndex, BTree deletedKeysBTree, IVirtualBufferCache vbc) {
        this.invIndex = invIndex;
        this.deletedKeysBTree = deletedKeysBTree;
        this.vbc = vbc;
    }

    public IInvertedIndex getInvIndex() {
        return invIndex;
    }

    public BTree getDeletedKeysBTree() {
        return deletedKeysBTree;
    }

    @Override
    protected boolean isFull() {
        return vbc.isFull();
    }

    @Override
    protected void reset() throws HyracksDataException {
        super.reset();
        invIndex.deactivate();
        invIndex.destroy();
        invIndex.create();
        invIndex.activate();
        deletedKeysBTree.deactivate();
        deletedKeysBTree.destroy();
        deletedKeysBTree.create();
        deletedKeysBTree.activate();
    }
}

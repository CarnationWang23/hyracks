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

package edu.uci.ics.hyracks.storage.am.lsm.btree.impls;

import edu.uci.ics.hyracks.storage.am.btree.api.IBTreeLeafFrame;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTree;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTreeOpContext;
import edu.uci.ics.hyracks.storage.am.common.api.IIndexOpContext;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.IndexOp;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.MultiComparator;

public final class LSMBTreeOpContext implements IIndexOpContext {
    
	public ITreeIndexFrameFactory insertLeafFrameFactory;
	public ITreeIndexFrameFactory deleteLeafFrameFactory;
	public IBTreeLeafFrame insertLeafFrame;
	public IBTreeLeafFrame deleteLeafFrame;
	public final BTree memBTree;
	public BTree.BTreeAccessor memBTreeAccessor;
	public BTreeOpContext memBTreeOpCtx;
	public IndexOp op;
	public final MultiComparator cmp;
	
    public LSMBTreeOpContext(BTree memBTree, ITreeIndexFrameFactory insertLeafFrameFactory,
            ITreeIndexFrameFactory deleteLeafFrameFactory) {
		this.cmp = MultiComparator.create(memBTree.getComparatorFactories());
        this.memBTree = memBTree;
		this.insertLeafFrameFactory = insertLeafFrameFactory;
		this.deleteLeafFrameFactory = deleteLeafFrameFactory;
		this.insertLeafFrame = (IBTreeLeafFrame) insertLeafFrameFactory.createFrame();
		this.deleteLeafFrame = (IBTreeLeafFrame) deleteLeafFrameFactory.createFrame();
		if (insertLeafFrame != null) {
			insertLeafFrame.setMultiComparator(cmp);
        }
        if (deleteLeafFrame != null) {
        	deleteLeafFrame.setMultiComparator(cmp);
        }
	}

    @Override
    public void reset(IndexOp newOp) {
    	this.op = newOp;
        switch (newOp) {
    	    case SEARCH:
    	        setMemBTreeAccessor();
    	        break;
    	        
    	    case DISKORDERSCAN:
    	    case UPDATE:
                // Attention: It is important to leave the leafFrame and
                // leafFrameFactory of the memBTree as is when doing an update.
                // Update will only be set if a previous attempt to delete or
                // insert failed, so we must preserve the semantics of the
                // previously requested operation.
    	        return;
    	        
    	    case INSERT:    	    
    	        setInsertMode();
    	        break;
    	        
    	    case DELETE:
                setDeleteMode();
                break;
    	}
    }
	
    private void setMemBTreeAccessor() {
        if (memBTreeAccessor == null) {
            memBTreeAccessor = (BTree.BTreeAccessor) memBTree.createAccessor();
            memBTreeOpCtx = memBTreeAccessor.getOpContext();
        }
    }
    
	public void setInsertMode() {
	    setMemBTreeAccessor();
	    memBTreeOpCtx.leafFrame = insertLeafFrame;
	    memBTreeOpCtx.leafFrameFactory = insertLeafFrameFactory;
	}
	
	public void setDeleteMode() {
	    setMemBTreeAccessor();
	    memBTreeOpCtx.leafFrame = deleteLeafFrame;
        memBTreeOpCtx.leafFrameFactory = deleteLeafFrameFactory;
	}

    @Override
    public void reset() {
    }
    
    public IndexOp getIndexOp() {
        return op;
    }
}
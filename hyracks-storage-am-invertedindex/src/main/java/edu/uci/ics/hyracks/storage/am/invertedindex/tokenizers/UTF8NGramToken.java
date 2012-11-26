/**
 * Copyright 2010-2011 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS"; BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations under
 * the License.
 * 
 * Author: Alexander Behm <abehm (at) ics.uci.edu>
 */

package edu.uci.ics.hyracks.storage.am.invertedindex.tokenizers;

import java.io.IOException;

import edu.uci.ics.hyracks.data.std.api.IMutableValueStorage;
import edu.uci.ics.hyracks.data.std.primitive.UTF8StringPointable;
import edu.uci.ics.hyracks.dataflow.common.data.util.StringUtils;

public class UTF8NGramToken extends AbstractUTF8Token implements INGramToken {

    public final static char PRECHAR = '#';

    public final static char POSTCHAR = '$';

    protected int numPreChars;
    protected int numPostChars;

    public UTF8NGramToken(byte tokenTypeTag, byte countTypeTag) {
        super(tokenTypeTag, countTypeTag);
    }

    @Override
    public int getNumPostChars() {
        return numPreChars;
    }

    @Override
    public int getNumPreChars() {
        return numPostChars;
    }

    @Override
    public void serializeToken(IMutableValueStorage outVal) throws IOException {
        handleTokenTypeTag(outVal.getDataOutput());
        int tokenUTF8LenOff = outVal.getLength();

        // regular chars
        int numRegChars = tokenLength - numPreChars - numPostChars;

        // assuming pre and post char need 1-byte each in utf8
        int tokenUTF8Len = numPreChars + numPostChars;

        // Write dummy UTF length which will be correctly set later.
        outVal.getDataOutput().writeShort(0);

        // pre chars
        for (int i = 0; i < numPreChars; i++) {
            StringUtils.writeCharAsModifiedUTF8(PRECHAR, outVal.getDataOutput());
        }

        int pos = start;
        for (int i = 0; i < numRegChars; i++) {
            char c = Character.toLowerCase(UTF8StringPointable.charAt(data, pos));
            tokenUTF8Len += StringUtils.writeCharAsModifiedUTF8(c, outVal.getDataOutput());
            pos += UTF8StringPointable.charSize(data, pos);
        }

        // post chars
        for (int i = 0; i < numPostChars; i++) {
            StringUtils.writeCharAsModifiedUTF8(POSTCHAR, outVal.getDataOutput());
        }

        // Set UTF length of token.
        outVal.getByteArray()[tokenUTF8LenOff] = (byte) ((tokenUTF8Len >>> 8) & 0xFF);
        outVal.getByteArray()[tokenUTF8LenOff + 1] = (byte) ((tokenUTF8Len >>> 0) & 0xFF);
    }

    public void setNumPrePostChars(int numPreChars, int numPostChars) {
        this.numPreChars = numPreChars;
        this.numPostChars = numPostChars;
    }
}

/*
 * Copyright 2009-2013 by The Regents of the University of California
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  you may obtain a copy of the License from
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package edu.uci.ics.hyracks.dataflow.common.data.parsers;

import edu.uci.ics.hyracks.data.std.primitive.ByteArrayPointable;
import org.junit.Test;

import javax.xml.bind.DatatypeConverter;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;

public class ByteArrayHexParserFactoryTest {

    public static byte[] subArray(byte[] bytes, int start) {
        return Arrays.copyOfRange(bytes, start, bytes.length);
    }

    @Test
    public void testExtractPointableArrayFromHexString() throws Exception {
        byte[] cache = new byte[] { };

        String empty = "";
        cache = ByteArrayHexParserFactory
                .extractPointableArrayFromHexString(empty.toCharArray(), 0, empty.length(), cache);

        assertTrue(ByteArrayPointable.getLength(cache, 0) == 0);
        assertTrue(DatatypeConverter.printHexBinary(subArray(cache, 2)).equalsIgnoreCase(empty));

        String everyChar = "ABCDEF0123456789";
        cache = ByteArrayHexParserFactory
                .extractPointableArrayFromHexString(everyChar.toCharArray(), 0, everyChar.length(), cache);
        assertTrue(ByteArrayPointable.getLength(cache, 0) == everyChar.length() / 2);
        assertTrue(DatatypeConverter.printHexBinary(subArray(cache, 2)).equalsIgnoreCase(everyChar));

        String lowercase = "0123456789abcdef";
        cache = ByteArrayHexParserFactory
                .extractPointableArrayFromHexString(lowercase.toCharArray(), 0, lowercase.length(), cache);
        assertTrue(ByteArrayPointable.getLength(cache, 0) == lowercase.length() / 2);
        assertTrue(DatatypeConverter.printHexBinary(subArray(cache, 2)).equalsIgnoreCase(lowercase));

        char[] maxChars = new char[(ByteArrayPointable.MAX_LENGTH - 1) * 2];
        Arrays.fill(maxChars, 'f');
        String maxString = new String(maxChars);
        cache = ByteArrayHexParserFactory
                .extractPointableArrayFromHexString(maxString.toCharArray(), 0, maxString.length(), cache);
        assertTrue(ByteArrayPointable.getLength(cache, 0) == maxString.length() / 2);
        assertTrue(DatatypeConverter.printHexBinary(subArray(cache, 2)).equalsIgnoreCase(maxString));
    }

    @Test
    public void testExtractByteArrayFromValidHexString() throws Exception {

        String hexString = "FFAB99213489";
        byte[] parsed = new byte[hexString.length() / 2];
        ByteArrayHexParserFactory
                .extractByteArrayFromValidHexString(hexString.toCharArray(), 0, hexString.length(), parsed, 0);
        assertTrue(Arrays.equals(parsed, DatatypeConverter.parseHexBinary(hexString)));

        byte[] parsed2 = new byte[] { };
        ByteArrayHexParserFactory.extractByteArrayFromValidHexString("".toCharArray(), 0, 0, parsed, 0);
        assertTrue(Arrays.equals(parsed2, DatatypeConverter.parseHexBinary("")));

        hexString = hexString.toLowerCase();
        ByteArrayHexParserFactory
                .extractByteArrayFromValidHexString(hexString.toCharArray(), 0, hexString.length(), parsed, 0);
        assertTrue(Arrays.equals(parsed, DatatypeConverter.parseHexBinary(hexString)));

        String mixString = "FFab9921ccCd";
        parsed = new byte[mixString.length() / 2];
        ByteArrayHexParserFactory
                .extractByteArrayFromValidHexString(mixString.toCharArray(), 0, mixString.length(), parsed, 0);
        assertTrue(Arrays.equals(parsed, DatatypeConverter.parseHexBinary(mixString)));
    }
}
/*
 * Copyright (C) 2014 Indeed Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
 package com.indeed.imhotep;

import com.google.common.base.Charsets;
import com.indeed.imhotep.api.FTGSIterator;
import com.indeed.imhotep.api.RawFTGSIterator;
import org.junit.Test;

import java.util.Collection;

/**
 * @author jsgroth
 */
public class RawFTGSMergerTest extends AbstractFTGSMergerCase {
    private static final String boldAlpha = codePointToString(0x1D6C2);

    @Override
    protected FTGSIterator newFTGSMerger(Collection<? extends RawFTGSIterator> iterators, int numStats) {
        return new RawFTGSMerger(iterators, numStats, null);
    }

    @Test
    public void testUTF8ToCodePoint() {
        for (int cp = 0x00000; cp <= 0xFFFFF; ++cp) {
            if (cp >= 0xD800 && cp <= 0xDFFF) continue;
            
            String s = codePointToString(cp);
            byte[] b = s.getBytes(Charsets.UTF_8);
            assertEquals(cp, RawFTGSMerger.UTF8ToCodePoint(b[0] & 0xFF, b, 1, b.length));
        }
    }

    @Test
    public void testCompareBytes() {
        {
            String s1 = codePointToString(0x0800);
            String s2 = codePointToString(0x0080);
            assertTrue(s1.compareTo(s2) > 0);
            assertTrue(s1.codePointAt(0) > s2.codePointAt(0));
            assertTrue(doCompare(s1, s2) > 0);
        }

        assertTrue(boldAlpha.compareTo(boldAlpha) == 0);
        assertTrue(doCompare(boldAlpha, boldAlpha) == 0);

        for (int cp = 0x0000; cp <= 0xD7FF; ++cp) {
            String s = codePointToString(cp);
            cmp1(s, boldAlpha, 0);
            cmp1("a" + s, "a" + boldAlpha, 1);
            cmp1(boldAlpha + s, boldAlpha + boldAlpha, 2);
        }

        for (int cp = 0xE000; cp <= 0xFFFF; ++cp) {
            String s = codePointToString(cp);
            cmp2(s, boldAlpha, 0);
            cmp2("a" + s, "a" + boldAlpha, 1);
            cmp2(boldAlpha + s, boldAlpha + boldAlpha, 2);
        }
    }

    private static void cmp2(String s, String s2, int cpindex) {
        assertTrue(s.compareTo(s2) > 0);
        assertTrue(s.codePointAt(cpindex) < s2.codePointAt(cpindex));
        assertTrue(doCompare(s, s2) > 0);
        assertTrue(doCompare(s2, s) < 0);
    }

    private static void cmp1(String s, String s2, int cpindex) {
        assertTrue(s.compareTo(s2) < 0);
        assertTrue(s.codePointAt(cpindex) < s2.codePointAt(cpindex));
        assertTrue(doCompare(s, s2) < 0);
        assertTrue(doCompare(s2, s) > 0);
    }

    private static int doCompare(String s1, String s2) {
        byte[] b1 = s1.getBytes(Charsets.UTF_8);
        byte[] b2 = s2.getBytes(Charsets.UTF_8);
        return RawFTGSMerger.compareBytes(b1, b1.length, b2, b2.length);
    }

    private static String codePointToString(int codePoint) {
        final char[] c = new char[2];
        final int len = Character.toChars(codePoint, c, 0);
        return new String(c, 0, len);
    }
}

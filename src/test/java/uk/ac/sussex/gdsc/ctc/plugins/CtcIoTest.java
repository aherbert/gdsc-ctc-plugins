/*-
 * #%L
 * Fiji plugins with supplementary functionality to CTC measures for
 *       quantitative evaluation of biomedical tracking.
 * %%
 * Copyright (C) 2024 Alex Herbert
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */
package uk.ac.sussex.gdsc.ctc.plugins;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link CtcIo} plugin.
 */
class CtcIoTest {
  @Test
  void testLoadNodeMap() throws IOException {
    List<int[]> list = CtcIo.loadNodeMap(new StringReader(""));
    Assertions.assertEquals(0, list.size());

    list = CtcIo.loadNodeMap(new StringReader("0 1 2\n3 5 7\n"));
    Assertions.assertEquals(2, list.size());
    Assertions.assertArrayEquals(new int[] {0, 1, 2}, list.get(0));
    Assertions.assertArrayEquals(new int[] {3, 5, 7}, list.get(1));
  }

  @Test
  void testLoadNodeMapThrows() throws IOException {
    // Create a node map
    Assertions.assertThrows(IOException.class,
        () -> CtcIo.loadNodeMap(new StringReader("0\n")));
    Assertions.assertThrows(IOException.class,
        () -> CtcIo.loadNodeMap(new StringReader("0 1\n")));
    Assertions.assertThrows(IOException.class,
        () -> CtcIo.loadNodeMap(new StringReader("0 a 2\n")));
    Assertions.assertThrows(IOException.class,
        () -> CtcIo.loadNodeMap(new StringReader("0 1 2 3\n")));
  }

  @Test
  void testLoadTracksFile() throws IOException {
    List<int[]> list = CtcIo.loadTracksFile(new StringReader(""));
    Assertions.assertEquals(0, list.size());

    list = CtcIo.loadTracksFile(new StringReader("0 1 2 0\n3 5 7 2\n"));
    Assertions.assertEquals(2, list.size());
    Assertions.assertArrayEquals(new int[] {0, 1, 2, 0}, list.get(0));
    Assertions.assertArrayEquals(new int[] {3, 5, 7, 2}, list.get(1));
  }

  @Test
  void testLoadTracksFileThrows() throws IOException {
    // Create a node map
    Assertions.assertThrows(IOException.class,
        () -> CtcIo.loadTracksFile(new StringReader("0\n")));
    Assertions.assertThrows(IOException.class,
        () -> CtcIo.loadTracksFile(new StringReader("0 1\n")));
    Assertions.assertThrows(IOException.class,
        () -> CtcIo.loadTracksFile(new StringReader("0 1 2\n")));
    Assertions.assertThrows(IOException.class,
        () -> CtcIo.loadTracksFile(new StringReader("0 a 2 3\n")));
    Assertions.assertThrows(IOException.class,
        () -> CtcIo.loadTracksFile(new StringReader("0 1 2 3 4\n")));
  }
}

/*-
 * #%L
 * Fiji plugins providing CTC measures for quantitative evaluation
 *         of biomedical tracking in general.
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * IO for Cell Tracking Challenge (CTC) data files.
 */
final class CtcIo {

  /** No instances. */
  private CtcIo() {}

  /**
   * Load the node map from {@code in}. Expects records of: resID time gtID.
   *
   * <pre>
   * 0 1 42
   * 0 2 42
   * 0 3 85
   * 1 1 13
   * </pre>
   *
   * <p>The records map the result ID to the ground-truth ID
   * for each time point of the result track.
   *
   * @param in the input
   * @return the list
   * @throws IOException Signals that an I/O exception has occurred.
   */
  static List<int[]> loadNodeMap(Reader in) throws IOException {
    final ArrayList<int[]> list = new ArrayList<>();
    try (BufferedReader r = new BufferedReader(in)) {
      for (String line = r.readLine(); line != null; line = r.readLine()) {
        final int i1 = line.indexOf(' ');
        final int i2 = line.indexOf(' ', i1 + 1);
        if ((i1 | i2) < 0) {
          throw new IOException("Invalid [id, t, class] record: " + line);
        }
        final int id = Integer.parseInt(line.substring(0, i1));
        final int t = Integer.parseInt(line.substring(i1 + 1, i2));
        final int c = Integer.parseInt(line.substring(i2 + 1));
        list.add(new int[] {id, t, c});
      }
    } catch (final NumberFormatException e) {
      throw new IOException(e);
    }
    return list;
  }

  /**
   * Load the CTC tracks file from {@code in}. Expects records of: ID start end parent.
   *
   * <pre>
   * 0 1 42 0
   * 1 43 53 0
   * </pre>
   *
   * @param in the input
   * @return the list
   * @throws IOException Signals that an I/O exception has occurred.
   */
  static List<int[]> loadTracksFile(Reader in) throws IOException {
    final ArrayList<int[]> list = new ArrayList<>();
    try (BufferedReader r = new BufferedReader(in)) {
      for (String line = r.readLine(); line != null; line = r.readLine()) {
        final int i1 = line.indexOf(' ');
        final int i2 = line.indexOf(' ', i1 + 1);
        final int i3 = line.indexOf(' ', i2 + 1);
        if ((i1 | i2 | i3) < 0) {
          throw new IOException("Invalid [id start end parent] record: " + line);
        }
        final int id = Integer.parseInt(line.substring(0, i1));
        final int s = Integer.parseInt(line.substring(i1 + 1, i2));
        final int e = Integer.parseInt(line.substring(i2 + 1, i3));
        final int p = Integer.parseInt(line.substring(i3 + 1));
        list.add(new int[] {id, s, e, p});
      }
    } catch (final NumberFormatException e) {
      throw new IOException(e);
    }
    return list;
  }
}

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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import net.celltrackingchallenge.measures.TRA;
import net.celltrackingchallenge.measures.TrackDataCache;
import net.celltrackingchallenge.measures.TrackDataCache.TemporalLevel;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.scijava.log.LogMessage;
import org.scijava.log.StderrLogService;

/**
 * Tests for {@link CtcHelper} plugin.
 */
class CtcHelperTest {
  /** Logger. Comment out the messageLogged method to view output from the AOGM calculation. */
  private static final StderrLogService LOG = new StderrLogService() {
    @Override
    protected void messageLogged(LogMessage message) {
      // Do nothing
    }
  };

  /** Path to an empty file. */
  private static Path empty;

  @BeforeAll
  static void setup() throws IOException {
    empty = Files.createTempFile("empty", "txt");
  }

  @AfterAll
  static void teardown() throws IOException {
    if (empty != null) {
      Files.delete(empty);
    }
  }

  @Test
  void testCreateTemporalLevel() {
    final TrackDataCache cache = new TrackDataCache(LOG);
    final int[] m_gt_lab = {0};
    final int[] m_gt_match = {1};
    final int[] m_res_lab = {2};
    @SuppressWarnings("unchecked")
    final HashSet<Integer>[] m_res_match = (HashSet<Integer>[]) new HashSet<?>[1];
    final TemporalLevel level =
        CtcHelper.createTemporalLevel(cache, 3, m_gt_lab, m_gt_match, m_res_lab, m_res_match);
    Assertions.assertNotNull(level);
  }

  @ParameterizedTest
  @ValueSource(ints = {1, 42})
  void testSingletonIntSet(int v) throws IOException, ClassNotFoundException {
    final HashSet<Integer> s = CtcHelper.hashset(v);
    // Test methods required by CTC plugins
    Assertions.assertEquals(1, s.size());
    Assertions.assertFalse(s.isEmpty());
    Assertions.assertTrue(s.contains(v));
    Assertions.assertFalse(s.contains(null));
    Assertions.assertFalse(s.contains(Integer.valueOf(v + 1)));
    Assertions.assertThrows(UnsupportedOperationException.class, () -> s.add(3));
    Assertions.assertThrows(UnsupportedOperationException.class, () -> s.add(v));
    Assertions.assertThrows(UnsupportedOperationException.class, () -> s.remove(v));
    Assertions.assertThrows(UnsupportedOperationException.class, () -> s.clear());
    Assertions.assertEquals(s, s.clone());

    // Iterator
    for (final Integer i : s) {
      Assertions.assertEquals(v, i);
    }
    Iterator<Integer> it = s.iterator();
    it.next();
    Assertions.assertThrows(NoSuchElementException.class, () -> it.next());
    it.forEachRemaining(i -> Assertions.fail());
    s.iterator().forEachRemaining(i -> Assertions.assertEquals(v, i));

    // Spliterator
    Assertions.assertArrayEquals(new int[] {v}, s.stream().mapToInt(x -> x).toArray());

    Assertions.assertArrayEquals(new Integer[] {v}, s.toArray());
    Assertions.assertArrayEquals(new Number[] {v}, s.toArray(new Number[0]));

    // Equals
    final HashSet<Integer> other = new HashSet<>();
    other.add(v);
    Assertions.assertEquals(other, s);

    // Serialization
    byte[] bytes;
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos)) {
      oos.writeObject(s);
      bytes = bos.toByteArray();
    }
    try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        ObjectInputStream ois = new ObjectInputStream(bis)) {
      @SuppressWarnings("unchecked")
      final
      HashSet<Integer> s2 = (HashSet<Integer>) ois.readObject();
      Assertions.assertEquals(s, s2);
    }
  }

  @Test
  void testTra() throws IOException {
    Path resTracks = null;
    Path gtTracks = null;
    Path map = null;
    try {
      resTracks = Files.createTempFile("res", "txt");
      gtTracks = Files.createTempFile("gt", "txt");
      map = Files.createTempFile("map", "txt");

      Files.write(resTracks, Arrays.asList("1 0 2 0", "2 3 5 1"));
      Files.write(gtTracks, Arrays.asList("10 0 1 0", "12 2 5 10", "44 1 1 0"));
      Files.write(map, Arrays.asList("1 0 10", "1 1 10", "1 2 12", "2 3 12", "2 4 12", "2 5 12"));

      final TRA tra = new TRA(LOG);

      tra.doConsistencyCheck = true;
      tra.doLogReports = true;
      tra.doMatchingReports = false;
      tra.doAOGM = true;
      tra.penalty = tra.new PenaltyConfig(5, 10, 1, 1, 1.5, 1.35);

      final String gtPath = gtTracks.toString();
      final String resPath = resTracks.toString();
      final String mapPath = map.toString();
      final double aogm = tra.calculate(null, null,
          CtcHelper.loadTrackDataCache(LOG, gtPath, resPath, mapPath));

      // Expected: 2 x wrong semantics (1.35) : 1 false nagative
      Assertions.assertEquals(2 * 1.35 + 10, aogm);

      // Check empty files raise exceptions
      String emptyFile = empty.toString();
      Assertions.assertThrows(IllegalArgumentException.class,
          () -> CtcHelper.loadTrackDataCache(LOG, gtPath, resPath, emptyFile));
      Assertions.assertThrows(IllegalArgumentException.class,
          () -> CtcHelper.loadTrackDataCache(LOG, emptyFile, resPath, mapPath));

    } finally {
      if (resTracks != null) {
        Files.delete(resTracks);
      }
      if (gtTracks != null) {
        Files.delete(gtTracks);
      }
      if (map != null) {
        Files.delete(map);
      }
    }
  }

  @Test
  void testAogmTraSampleData() throws IOException {
    final TRA tra = new TRA(LOG);

    tra.doConsistencyCheck = true;
    tra.doLogReports = true;
    tra.doMatchingReports = false;
    tra.doAOGM = true;
    tra.penalty = tra.new PenaltyConfig(5, 10, 1, 1, 1.5, 1.0);

    final String gtPath = this.getClass().getResource("man_track.txt").getPath();
    final String resPath = this.getClass().getResource("res_track.txt").getPath();
    final String nodeMapping = this.getClass().getResource("map.txt").getPath();
    final double aogm = tra.calculate(null, null,
        CtcHelper.loadTrackDataCache(LOG, gtPath, resPath, nodeMapping));

    // Validated result using original CTC ground-truth and result data
    Assertions.assertEquals(20, aogm);
  }

  @Test
  void testAogmCalculator() throws IOException {
    final TRA tra = new TRA(LOG);

    tra.doConsistencyCheck = true;
    tra.doLogReports = true;
    tra.doMatchingReports = false;
    tra.doAOGM = true;
    tra.penalty = tra.new PenaltyConfig(5, 10, 1, 1, 1.5, 1.0);

    final String gtPath = this.getClass().getResource("man_track.txt").getPath();
    final String resPath = this.getClass().getResource("res_track.txt").getPath();
    final String nodeMapping = this.getClass().getResource("map.txt").getPath();

    final AogmCalculator calc = AogmCalculator.create(gtPath, tra, LOG, 10, 1.5);
    final double aogm = calc.calculate(resPath, nodeMapping);

    // Validated result using original CTC ground-truth and result data
    Assertions.assertEquals(20, aogm);
    Assertions.assertEquals(29926.5, calc.getAogmEmpty());
    Assertions.assertEquals(0.9993316959885051, calc.getTra(aogm));

    // Check empty files raise exceptions
    String emptyFile = empty.toString();
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> AogmCalculator.create(emptyFile, tra, LOG, 10, 1.5));
    Assertions.assertThrows(IllegalArgumentException.class,
        () -> calc.calculate(resPath, emptyFile));
  }
}

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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import net.celltrackingchallenge.measures.TRA;
import net.celltrackingchallenge.measures.TrackDataCache;
import org.scijava.log.LogService;

/**
 * Helper class for computing the AOGM metric for many results against the same ground-truth data.
 */
final class AogmCalculator {
  /** The ground truth tracks. Records of: id start end parent. */
  private final List<int[]> gtTracks;
  /** TRA instance for calculations. */
  private final TRA tra;
  /** The cache. */
  private final TrackDataCache cache;
  /** AOGM empty score. */
  private final double aogme;
  /** BitSet for working. */
  private final BitSet set;

  /**
   * Create an instance.
   *
   * @param gtTracks the ground-truth tracks
   * @param tra the TRA instance
   * @param cache the cache
   * @param aogme the AOGM empty score
   */
  private AogmCalculator(List<int[]> gtTracks, TRA tra, TrackDataCache cache, double aogme) {
    this.gtTracks = gtTracks;
    this.tra = tra;
    this.cache = cache;
    this.aogme = aogme;
    // Always compute the AOGM
    this.tra.doAOGM = true;
    // BitSet that can store the maximum ground-truth ID
    set = new BitSet(gtTracks.stream().mapToInt(x -> x[0]).max().orElse(1));
  }

  /**
   * Creates the calculator.
   * 
   * <p>Accepts additional parameters as these are package-private and cannot be obtained from the
   * TRA instance. The penalty parameters are used to compute the AOGM empty score.
   *
   * @param gtTracksPath the ground truth tracks
   * @param tra TRA class (configured for logging and with the appropriate penalty)
   * @param log the log service
   * @param fn the false negative penalty
   * @param ea the edge add penalty
   * @return the calculator
   * @throws IOException Signals that an I/O exception has occurred.
   */
  static AogmCalculator create(String gtTracksPath, TRA tra, LogService log, double fn, double ea)
      throws IOException {
    List<int[]> gtTracks;
    try (BufferedReader r = Files.newBufferedReader(Paths.get(gtTracksPath))) {
      gtTracks = CtcIo.loadTracksFile(r);
    }
    if (gtTracks.isEmpty()) {
      throw new IllegalArgumentException("No reference (GT) track was found!");
    }

    final TrackDataCache cache = new TrackDataCache(log) {
      // Ignore the validFor check so the cache is used as-is
      @Override
      public boolean validFor(String gtPath, String resPath) {
        return true;
      }
    };
    cache.LoadTrackFile(gtTracksPath, cache.gt_tracks);

    // Cost to create the result from scratch
    // how many parental links to add
    int numPar = 0;
    // how many track links (edges) to add
    long sum = 0;

    for (final int[] x : gtTracks) {
      // Length: end - start
      sum += x[2] - x[1];
      // Parent count
      if (x[3] > 0) {
        ++numPar;
      }
    }
    // adding nodes + adding edges
    final double aogme = fn * (sum + gtTracks.size()) + ea * (sum + numPar);

    return new AogmCalculator(gtTracks, tra, cache, aogme);
  }

  /**
   * Gets the AOGM empty score. The TRA score is:
   *
   * <pre>
   * max(0, 1 - aogm / aogme)
   * </pre>
   *
   * @return the AOGM empty
   */
  double getAogmEmpty() {
    return aogme;
  }

  /**
   * Gets the TRA score.
   *
   * @param aogm the AOGM
   * @return the TRA
   * @see #getAogmEmpty()
   */
  double getTra(double aogm) {
    return Math.max(0, 1 - aogm / aogme);
  }

  /**
   * Calculate the AOGM for the results.
   *
   * @param resTracksPath the result tracks
   * @param nodeMapping the node mapping
   * @return the AOGM
   * @throws IOException Signals that an I/O exception has occurred.
   */
  double calculate(String resTracksPath, String nodeMapping) throws IOException {
    // Get the node mapping: resID time gtID
    List<int[]> map;
    try (BufferedReader r = Files.newBufferedReader(Paths.get(nodeMapping))) {
      map = CtcIo.loadNodeMap(r);
    }
    if (map.isEmpty()) {
      throw new IllegalArgumentException("No result to GT mapping was found!");
    }

    // Here we have to do all the work done in
    // TrackDataCache.calculate(String gtPath, String resPath)
    // The paths are typically directories containing:
    // gtPath + "/TRA/man_track.txt"
    // resPath + "/res_track.txt"
    // Here we just have the track files themselves
    // cache.gt_tracks is already loaded
    cache.LoadTrackFile(resTracksPath, cache.res_tracks);

    // This is where we avoid the call to ClassifyLabels for each
    // frame in the GT and result masks and use the node mapping.

    // Process records [resID t gtID] in time order:
    map.sort(Comparator.comparingInt(x -> x[1]));
    int from = 0;
    int time = map.get(from)[1];
    for (int i = 1; i < map.size(); i++) {
      final int[] x = map.get(i);
      if (x[1] > time) {
        CtcHelper.classfifyLabels(map.subList(from, i), gtTracks, cache, time, set);
        time = x[1];
        from = i;
      }
    }
    CtcHelper.classfifyLabels(map.subList(from, map.size()), gtTracks, cache, time, set);

    // No exceptions raised for cache.levels.isEmpty() as we had a non-empty map

    cache.DetectForks(cache.gt_tracks, cache.gt_forks);
    cache.DetectForks(cache.res_tracks, cache.res_forks);

    return tra.calculate(null, null, cache);
  }
}

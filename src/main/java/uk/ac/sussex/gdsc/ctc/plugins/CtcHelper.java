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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Consumer;
import net.celltrackingchallenge.measures.TrackDataCache;
import net.celltrackingchallenge.measures.TrackDataCache.TemporalLevel;
import org.scijava.log.Logger;

/**
 * Helper class for Cell Tracking Challenge (CTC) analysis.
 */
final class CtcHelper {

  /** No instances. */
  private CtcHelper() {}

  /**
   * Load a track data cache from the provided ground-truth and result track files and a mapping
   * between the result node IDs and the ground-truth IDs.
   *
   * @param log the log
   * @param gtTracksPath the ground truth tracks
   * @param resTracksPath the result tracks
   * @param nodeMapping the node mapping
   * @return the track data cache
   * @throws IOException Signals that an I/O exception has occurred.
   */
  static TrackDataCache loadTrackDataCache(Logger log, String gtTracksPath, String resTracksPath,
      String nodeMapping) throws IOException {
    // Get the node mapping: resID time gtID
    List<int[]> map;
    try (BufferedReader r = Files.newBufferedReader(Paths.get(nodeMapping))) {
      map = CtcIo.loadNodeMap(r);
    }
    if (map.isEmpty()) {
      throw new IllegalArgumentException("No result to GT mapping was found!");
    }

    final TrackDataCache cache = new TrackDataCache(log) {
      // Ignore the validFor check so the cache is used as-is
      @Override
      public boolean validFor(String gtPath, String resPath) {
        return true;
      }
    };

    // We load this separately as we have to know every frame that contains a GT track
    // and the cache.gt_tracks Track objects have package-private members
    List<int[]> gtTracks;
    try (BufferedReader r = Files.newBufferedReader(Paths.get(gtTracksPath))) {
      gtTracks = CtcIo.loadTracksFile(r);
    }
    if (gtTracks.isEmpty()) {
      throw new IllegalArgumentException("No reference (GT) track was found!");
    }

    // Here we have to do all the work done in
    // TrackDataCache.calculate(String gtPath, String resPath)
    // The paths are typically directories containing:
    // gtPath + "/TRA/man_track.txt"
    // resPath + "/res_track.txt"
    // Here we just have the track files themselves
    cache.LoadTrackFile(gtTracksPath, cache.gt_tracks);
    cache.LoadTrackFile(resTracksPath, cache.res_tracks);

    // BitSet that can store the maximum ground-truth ID
    final BitSet set = new BitSet(gtTracks.stream().mapToInt(x -> x[0]).max().orElse(1));

    // This is where we avoid the call to ClassifyLabels for each
    // frame in the GT and result masks and use the node mapping.

    // Process records [resID t gtID] in time order:
    map.sort(Comparator.comparingInt(x -> x[1]));
    int from = 0;
    int time = map.get(from)[1];
    for (int i = 1; i < map.size(); i++) {
      final int[] x = map.get(i);
      if (x[1] > time) {
        classfifyLabels(map.subList(from, i), gtTracks, cache, time, set);
        time = x[1];
        from = i;
      }
    }
    classfifyLabels(map.subList(from, map.size()), gtTracks, cache, time, set);

    // No exceptions raised for cache.levels.isEmpty() as we had a non-empty map

    cache.DetectForks(cache.gt_tracks, cache.gt_forks);
    cache.DetectForks(cache.res_tracks, cache.res_forks);

    return cache;
  }

  /**
   * Classfify the labels.
   *
   * <p>Re-uses a BitSet that can store all the ground-truth IDs.
   *
   * @param map the map of result ID to ground-truth ID
   * @param gtTracks the ground-truth tracks
   * @param cache the cache
   * @param time the time point
   * @param gtSet the ground-truth set
   */
  @SuppressWarnings("unchecked")
  static void classfifyLabels(List<int[]> map, List<int[]> gtTracks, TrackDataCache cache,
      int time, BitSet gtSet) {
    // Assume the size of the map is the number of result labels.
    // Count the number of GT labels in this frame.
    // track record: id start end parent
    int gtCount = (int) gtTracks.stream().filter(x -> x[1] <= time && time <= x[2]).count();

    // mapping from the label in the frame to the zero-based ID: lbl -> idx
    final int[] gtLabel = new int[gtCount];
    final int[] resLabel = new int[map.size()];

    // each zero-based ID matches only 1 result
    final int[] gtMatch = new int[gtCount];
    Arrays.fill(gtMatch, -1);
    // each zero-based ID matches 1-or-many results
    final HashSet<Integer>[] resMatch = (HashSet<Integer>[]) new HashSet<?>[map.size()];

    // All the results will be in the map.
    // Most of the ground-truth will be in the map and we track those that are observed.
    gtSet.clear();
    for (int i = 0; i < map.size(); i++) {
      int[] x = map.get(i);
      gtLabel[i] = x[2];
      resLabel[i] = x[0];
      gtMatch[i] = i;
      // Cannot use Collections.singleton as the temporalLevel requires a HashSet.
      // So create a special singleton.
      resMatch[i] = new SingletonIntSet(i);
      // Mark this ground-truth label as observed
      gtSet.set(x[2]);
    }

    // Not all the ground truth will be in the map. If we do not create them then
    // we will fail the consistency check. So add a lbl -> idx mapping for those
    // not observed.
    final int[] index = {map.size() - 1};
    gtTracks.forEach(x -> {
      // track record: id start end parent
      if (x[1] <= time && time <= x[2] && !gtSet.get(x[0])) {
        gtLabel[++index[0]] = x[0];
      }
    });

    // TemporalLevel constructor and fields are package-private. Options:
    // 1. Create this class in the same package namespace.
    // 2. Duplicate the entire code (there is a lot) for the computation.
    // 3. Use reflection to create a TemporalLevel and write to it.
    // Here we use option 3.
    cache.levels.add(createTemporalLevel(cache, time, gtLabel, gtMatch, resLabel, resMatch));
  }

  /**
   * Creates the temporal level using reflection.
   *
   * @param cache the cache instance
   * @param time the time
   * @param gtLabel zero-based id for the GT label in this time point
   * @param gtMatch the zero-based result id that matches the GT label
   * @param resLabel zero-based id for the result label in this time point
   * @param resMatch the zero-based GT ids that matches the result label
   * @return the temporal level
   */
  static TemporalLevel createTemporalLevel(TrackDataCache cache, int time, int[] gtLabel,
      int[] gtMatch, int[] resLabel, HashSet<Integer>[] resMatch) {
    try {
      final Constructor<TemporalLevel> constructor =
          TemporalLevel.class.getDeclaredConstructor(TrackDataCache.class, int.class);
      constructor.setAccessible(true);
      final TemporalLevel level = constructor.newInstance(cache, time);

      Field f = TemporalLevel.class.getDeclaredField("m_gt_lab");
      f.setAccessible(true);
      f.set(level, gtLabel);

      f = TemporalLevel.class.getDeclaredField("m_gt_match");
      f.setAccessible(true);
      f.set(level, gtMatch);

      f = TemporalLevel.class.getDeclaredField("m_res_lab");
      f.setAccessible(true);
      f.set(level, resLabel);

      f = TemporalLevel.class.getDeclaredField("m_res_match");
      f.setAccessible(true);
      f.set(level, resMatch);

      return level;
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException | NoSuchMethodException | SecurityException
        | NoSuchFieldException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Return a {@link HashSet} of a single int value.
   *
   * @param v the value
   * @return the hash set
   */
  static HashSet<Integer> hashset(int v) {
    return new SingletonIntSet(v);
  }

  /**
   * A singleton {@code HashSet<Integer>}.
   *
   * <p>Implements the minimum functionality required for the CTC analysis.
   */
  private static class SingletonIntSet extends HashSet<Integer> {
    /** serialVersionUID. */
    private static final long serialVersionUID = 20240305L;

    /** Single element. */
    private final int element;

    /**
     * @param v Value.
     */
    SingletonIntSet(int v) {
      super(0);
      element = v;
    }

    // Override HashSet implementations

    @Override
    public int size() {
      return 1;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public boolean contains(Object o) {
      return o instanceof Integer && ((Integer) o).intValue() == element;
    }

    @Override
    public boolean add(Integer e) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Object clone() {
      // Object never changes
      return this;
    }

    // java.io.Serializable works with defaultWriteObject

    @Override
    public Iterator<Integer> iterator() {
      return new Iterator<Integer>() {
        /** Flag to indicate the iterator has a next element. */
        private boolean hasNext = true;

        @Override
        public boolean hasNext() {
          return hasNext;
        }

        @Override
        public Integer next() {
          if (hasNext) {
            hasNext = false;
            return element;
          }
          throw new NoSuchElementException();
        }

        @Override
        public void forEachRemaining(Consumer<? super Integer> action) {
          if (hasNext) {
            action.accept(element);
            hasNext = false;
          }
        }
      };
    }

    @Override
    public Spliterator<Integer> spliterator() {
      return Spliterators.spliterator(toArray(),
          Spliterator.IMMUTABLE | Spliterator.ORDERED | Spliterator.DISTINCT | Spliterator.NONNULL);
    }

    @Override
    public Object[] toArray() {
      // This method is used in output reporting by the TRA class
      return new Object[] {element};
    }

    @Override
    public <T> T[] toArray(T[] a) {
      return Arrays.asList(Integer.valueOf(element)).toArray(a);
    }
  }
}

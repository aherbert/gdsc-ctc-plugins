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

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.stream.DoubleStream;
import java.util.stream.Stream;
import net.celltrackingchallenge.measures.TRA;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;

/**
 * Plugin to compute the AOGM measure on many result tracks against a single groud-truth.
 *
 * <p>Adapts the functionality in {@code net.celltrackingchallenge.fiji.plugins.plugin_AOGMmeasure}.
 *
 * <p>This class loads the mapping between result and ground-truth from file. Computation of the
 * AOGM score is then performed using the original code. The plugin can be used when tracking has
 * been performed many times on the same input (ground-truth) objects. The output tracks can then be
 * compared with ground-truth tracks to determine the best tracking result.
 */
@Plugin(type = Command.class, menuPath = "Plugins>Tracking>AOGM: Tracking measure (mapped batch)",
    name = "CTC_AOGM_Mapped_Batch", headless = true,
    description = "Calculates the AOGM tracking performance measure from the AOGM paper\n"
        + "using a mapping between result IDs and ground-truth IDs")
public class FileMatchedAogmMeasureBatch implements Command {

  @Parameter
  private LogService log;

  @Parameter(label = "Path to ground-truth result tracks:", style = FileWidget.OPEN_STYLE,
      description = "Path should contain track text file with records: id start end parent",
      persistKey = "ctc_gt_tracks")
  private File gtPath;

  @Parameter(label = "Path to result tracks folder:", style = FileWidget.DIRECTORY_STYLE,
      description = "Contains track .txt file with records: id start end parent and "
          + ".map.txt file with records: redId time gtId",
      persistKey = "ctc_res_tracks_folder")
  private File resFolderPath;

  @Parameter(label = "Path to saved results:", style = FileWidget.OPEN_STYLE,
      description = "Path to save the results with records: file AOGM AOGMe TRA",
      persistKey = "ctc_aogm_results")
  private File resultPath;

  @Parameter(label = "Penalty preset:",
      choices = {"Cell Tracking Challenge", "use the values below"}, callback = "onPenaltyChange")
  private String penaltyModel;

  @Parameter(label = "Splitting operations penalty:", min = "0.0", callback = "onWeightChange")
  private double ns = 5.0;

  @Parameter(label = "False negative vertices penalty:", min = "0.0", callback = "onWeightChange")
  private double fn = 10.0;

  @Parameter(label = "False positive vertices penalty:", min = "0.0", callback = "onWeightChange")
  private double fp = 1.0;

  @Parameter(label = "Redundant edges to be deleted penalty:", min = "0.0",
      callback = "onWeightChange")
  private double ed = 1.0;

  @Parameter(label = "Edges to be added penalty:", min = "0.0", callback = "onWeightChange")
  private double ea = 1.5;

  @Parameter(label = "Edges with wrong semantics penalty:", min = "0.0",
      callback = "onWeightChange")
  private double ec = 1.0;

  @Parameter(label = "Consistency check of input data:",
      description = "Checks multiple consistency-oriented criteria on both input and GT data.")
  private boolean doConsistencyCheck = false;

  @Parameter(label = "Verbose report on tracking errors:",
      description = "Logs all discrepancies (and organizes them by category) between the input and GT data.")
  private boolean doLogReports = false;

  @Parameter(label = "Verbose report on matching of segments:",
      description = "Logs which RES/GT segment maps onto which GT/RES in the data.")
  private boolean doMatchingReports = false;

  @SuppressWarnings("unused")
  private void onPenaltyChange() {
    if (penaltyModel.startsWith("Cell Tracking Challenge")) {
      ns = 5.0;
      fn = 10.0;
      fp = 1.0;
      ed = 1.0;
      ea = 1.5;
      ec = 1.0;
    } else if (penaltyModel.startsWith("some other future preset")) {
      ns = 99.0;
    }
  }

  @SuppressWarnings("unused")
  private void onWeightChange() {
    penaltyModel = "use the values below";
  }

  @Override
  public void run() {
    // start up the worker class
    final TRA tra = new TRA(log);

    // set up its operational details
    // Note: the calculator always computes the AOGM
    tra.doConsistencyCheck = doConsistencyCheck;
    tra.doLogReports = doLogReports;
    tra.doMatchingReports = doMatchingReports;

    // also the AOGM weights
    final TRA.PenaltyConfig penalty = tra.new PenaltyConfig(ns, fn, fp, ed, ea, ec);
    tra.penalty = penalty;

    // Collect the results to allow sorting them for convenience
    final ArrayList<AogmResult> results = new ArrayList<>();
    long gtNodes;
    long gtEdges;
    double aogme;

    try (Stream<Path> files = Files.list(resFolderPath.toPath())) {

      // do the calculation
      // Here we use our own calculator that caches ground-truth information for re-use.
      final AogmCalculator calc = AogmCalculator.create(gtPath.getPath(), tra, log);
      gtNodes = calc.getGtNodeCount();
      gtEdges = calc.getGtEdgeCount();
      aogme = gtNodes * fn + gtEdges * ea;

      // Iterate over the [track.txt, track.map.txt] pairs.
      // Processing inside the stream throws unchecked IO exceptions.
      // Track the scores
      final DoubleStream.Builder builder1 = DoubleStream.builder();
      final DoubleStream.Builder builder2 = DoubleStream.builder();
      files.map(Path::toString).filter(s -> s.endsWith(".map.txt")).forEach(mapPath -> {
        // Find corresponding .txt file
        final String resPath = mapPath.replace(".map.txt", ".txt");
        if (Files.isReadable(Paths.get(resPath))) {
          try {
            final double aogm = calc.calculate(resPath, mapPath);
            final double traScore = AogmCalculator.getTra(aogm, aogme);
            final StringBuilder sb = new StringBuilder(256);
            sb.append(Paths.get(resPath).getFileName())
              .append(',').append(aogm)
              .append(',').append(traScore);
            if (doLogReports) {
              // Use the log reports to collect the count of each error.
              // We must ignore the header.
              // Note: Ideally we could get these without logging but this requires
              // a code change in the TRA class to count these errors.
              sb.append(',').append(tra.logNS.size() - 1)
                .append(',').append(tra.logFN.size() - 1)
                .append(',').append(tra.logFP.size() - 1)
                .append(',').append(tra.logED.size() - 1)
                .append(',').append(tra.logEA.size() - 1)
                .append(',').append(tra.logEC.size() - 1);
            }
            builder1.accept(aogm);
            builder2.accept(traScore);
            results.add(new AogmResult(traScore, sb.toString()));
          } catch (final IOException e) {
            throw new UncheckedIOException(e);
          }
        } else {
          log.error(
              String.format("Mapping file (%s) is missing track file (%s)", mapPath, resPath));
        }
      });

      // Summary stats
      final DoubleSummaryStatistics stats1 = builder1.build().summaryStatistics();
      final DoubleSummaryStatistics stats2 = builder2.build().summaryStatistics();
      log.info("n=" + stats2.getCount());
      log.info(String.format("AOGM max=%s; mean=%.5f; min=%s", stats1.getMax(), stats1.getAverage(),
          stats1.getMin()));
      log.info(String.format("TRA  min=%.5f; mean=%.5f; max=%.5f", stats2.getMin(),
          stats2.getAverage(), stats2.getMax()));
    } catch (final Exception e) {
      log.error("AOGM error: " + e.getMessage(), e);
      return;
    }

    // Sort
    results.sort(Comparator.comparingDouble(AogmResult::getScore).reversed());

    try (BufferedWriter bw = Files.newBufferedWriter(resultPath.toPath())) {

      // Header
      bw.write("# GT = " + gtPath.toString());
      bw.newLine();
      bw.write("# GT nodes = " + gtNodes);
      bw.newLine();
      bw.write("# GT edges = " + gtEdges);
      bw.newLine();
      bw.write("# Penalty [ns, fn, fp, ed, ea, ec] = "
          + Arrays.toString(new double[] {ns, fn, fp, ed, ea, ec}));
      bw.newLine();
      bw.write("# AOGM_e = " + aogme);
      bw.newLine();
      bw.write("# Result dir = " + resFolderPath.toString());
      bw.newLine();
      bw.write("Tracks,AOGM,TRA");
      if (doLogReports) {
        bw.write(",NS,FN,FP,ED,EA,EC");
      }
      bw.newLine();

      for (final AogmResult r : results) {
        bw.append(r.result);
        bw.newLine();
      }

      log.info("Saved AOGM result file: " + resultPath.toPath());
    } catch (final Exception e) {
      log.error("AOGM error: " + e.getMessage(), e);
    }
  }

  /**
   * Data class for sorting AOGM results.
   */
  private static class AogmResult {
    final double score;
    final String result;

    AogmResult(double score, String result) {
      this.score = score;
      this.result = result;
    }

    double getScore() {
      return score;
    }
  }
}

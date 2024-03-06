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

import java.io.File;
import net.celltrackingchallenge.measures.TRA;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;

/**
 * This class is a copy implementation of
 * {@code net.celltrackingchallenge.fiji.plugins.plugin_AOGMmeasure}. Instead of loading all the
 * tiff mask images and computing the overlap between masks to obtain the mapping from result tracks
 * to ground-truth track, it loads the mapping from file. Computation of the AOGM score is then
 * performed using the original code.
 */
@Plugin(type = Command.class, menuPath = "Plugins>Tracking>AOGM: Tracking measure (mapped)",
    name = "CTC_AOGM_Mapped", headless = true,
    description = "Calculates the AOGM tracking performance measure from the AOGM paper\n"
        + "using a mapping between result IDs and ground-truth IDs")
public class FileMatchedAogmMeasure implements Command {

  @Parameter
  private LogService log;

  @Parameter(label = "Path to ground-truth result tracks:", style = FileWidget.OPEN_STYLE,
      description = "Path should contain track text file with records: id start end parent",
      persistKey = "ctc_gt_tracks")
  private File gtPath;

  @Parameter(label = "Path to computed result tracks:", style = FileWidget.OPEN_STYLE,
      description = "Path should contain track text file with records: id start end parent",
      persistKey = "ctc_res_tracks")
  private File resPath;

  @Parameter(label = "Path to result to ground-truth mapping:", style = FileWidget.OPEN_STYLE,
      description = "Path should contain mapping text file with records: resId time gtId",
      persistKey = "ctc_map")
  private File mapPath;

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
  private boolean doConsistencyCheck = true;

  @Parameter(label = "Verbose report on tracking errors:",
      description = "Logs all discrepancies (and organizes them by category) between the input and GT data.")
  private boolean doLogReports = true;

  @Parameter(label = "Verbose report on matching of segments:",
      description = "Logs which RES/GT segment maps onto which GT/RES in the data.")
  private boolean doMatchingReports = false;

  @Parameter(label = "Do 1.0-min(AOGM,AOGM_empty)/AOGM_empty (TRA):",
      description = "The Cell Tracking Challenge TRA is exactly a normalized AOGM with specific penalties. If checked, returns between 0.0 to 1.0, higher is better.")
  private boolean doTRAnormalization = false;

  //hidden output values
  @Parameter(type = ItemIO.OUTPUT)
  String gtTracks;
  @Parameter(type = ItemIO.OUTPUT)
  String resTracks;
  @Parameter(type = ItemIO.OUTPUT)
  String resMap;
  @Parameter(type = ItemIO.OUTPUT)
  private double aogm = -1;

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
    gtTracks  = gtPath.getPath();
    resTracks = resPath.getPath();
    resMap = mapPath.getPath();

    try {
      // start up the worker class
      final TRA tra = new TRA(log);

      // set up its operational details
      tra.doConsistencyCheck = doConsistencyCheck;
      tra.doLogReports = doLogReports;
      tra.doMatchingReports = doMatchingReports;
      tra.doAOGM = !doTRAnormalization;

      // also the AOGM weights
      final TRA.PenaltyConfig penalty = tra.new PenaltyConfig(ns, fn, fp, ed, ea, ec);
      tra.penalty = penalty;

      // do the calculation
      // XXX: Here we load our own TrackDataCache with a text file to define the mapping
      // between result tracks and ground-truth tracks. The cache returns as
      // valid so we do not pass in paths to the calculate method.
      aogm = tra.calculate(null, null,
          CtcHelper.loadTrackDataCache(log, gtTracks, resTracks, resMap));

      // do not report anything explicitly (unless special format for parsing is
      // desired) as ItemIO.OUTPUT will make it output automatically
    } catch (final RuntimeException e) {
      log.error("AOGM problem: " + e.getMessage(), e);
    } catch (final Exception e) {
      log.error("AOGM error: " + e.getMessage(), e);
    }
  }
}

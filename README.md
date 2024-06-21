GDCS CTC Plugins
----------------

[![Java CI](https://github.com/aherbert/gdsc-ctc-plugins/actions/workflows/build.yml/badge.svg)](https://github.com/aherbert/gdsc-ctc-plugins/actions/workflows/build.yml)
[![Coverage Status](https://codecov.io/gh/aherbert/gdsc-ctc-plugins/branch/main/graph/badge.svg)](https://app.codecov.io/gh/aherbert/gdsc-ctc-plugins)
[![License](http://img.shields.io/:license-mit-blue.svg)](https://opensource.org/license/mit)

This is a repository with [Fiji](http://fiji.sc) plugins related to the
[Cell Tracking Challenge](http://www.celltrackingchallenge.net).

The plugins supplement the functionality of the
[CTC Fiji plugins](https://github.com/CellTrackingChallenge/fiji-plugins) by providing
alternative methods to load the object graphs used to compute the CTC measures.

See also [the CTC measures repository](https://github.com/CellTrackingChallenge/measures).

Installation
------------

The plugin can be installed into a ImageJ instance using Maven. This requires a
Java Development Kit (JDK) and [Maven](https://maven.apache.org/).

To install into an ImageJ instance at `/usr/local/fiji`:

    mvn package scijava:populate-app -Dscijava.app.directory=/usr/local/fiji

This will build the plugin Java archive (jar) and copy it and all the dependencies into
the ImageJ location. The plugins will appear at:

 * `Plugins > Tracking > AOGM: Tracking measure (mapped)`
 * `Plugins > Tracking > AOGM: Tracking measure (mapped batch)`

package org.honton.chas.updates;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.reporting.MavenReportException;

/** Renderer for xml report report about available dependency/dependency management updates. */
public class UpdatesRenderer {

  private final Map<Artifact, Updates> updates;
  private final File reportFile;
  private final StringBuilder sb = new StringBuilder();

  public UpdatesRenderer(File reportFile, Map<Artifact, Updates> updates) {
    this.updates = updates;
    this.reportFile = reportFile;
  }

  private void startElement(TabStops tabStops, String element) {
    sb.append(tabStops).append('<').append(element).append(">\n");
  }

  private void endElement(String tag) {
    sb.append("</").append(tag).append(">\n");
  }

  private void endElement(TabStops tabStops, String element) {
    sb.append(tabStops);
    endElement(element);
  }

  private void renderElement(TabStops tabStops, String element, Object value) {
    if (value != null) {
      startElement(tabStops, element);
      sb.setLength(sb.length() - 1);
      sb.append(value);
      endElement(element);
    }
  }

  private void renderSummary() {
    int incremental = 0;
    int minor = 0;
    int major = 0;
    int fractional = 0;
    int latest = 0;
    for (Updates available : updates.values()) {
      switch (available.getSmallestDifference()) {
        case Major:
          major++;
          break;
        case Minor:
          minor++;
          break;
        case Incremental:
          incremental++;
          break;
        case SubIncremental:
          fractional++;
          break;
        case Equal:
          latest++;
          break;
      }
    }

    startElement(TabStops.ONE, "summary");
    renderElement(TabStops.TWO, "usingLastVersion", latest);
    renderElement(TabStops.TWO, "nextVersionAvailable", fractional);
    renderElement(TabStops.TWO, "nextIncrementalAvailable", incremental);
    renderElement(TabStops.TWO, "nextMinorAvailable", minor);
    renderElement(TabStops.TWO, "nextMajorAvailable", major);
    endElement(TabStops.ONE, "summary");
  }

  private void renderAvailableVersions(Updates versions) {
    renderElement(TabStops.THREE, "currentVersion", versions.getCurrentVersion());
    String status = "no new available";
    ArtifactVersion nextVersion = versions.getNextVersion();
    if (nextVersion != null) {
      renderElement(TabStops.THREE, "nextVersion", nextVersion);

      boolean hasIncremental = renderMagnitude(versions, Magnitude.Incremental);
      boolean hasMinor = renderMagnitude(versions, Magnitude.Minor);
      boolean hasMajor = renderMagnitude(versions, Magnitude.Major);

      if (hasIncremental) {
        status = "incremental available";
      } else if (hasMinor) {
        status = "minor available";
      } else if (hasMajor) {
        status = "major available";
      }
    }
    renderElement(TabStops.THREE, "status", status);
  }

  private void renderDependencies() {
    startElement(TabStops.ONE, "dependencies");
    for (Entry<Artifact, Updates> entry : updates.entrySet()) {
      startElement(TabStops.TWO, "dependency");

      Artifact dependency = entry.getKey();
      renderElement(TabStops.THREE, "groupId", dependency.getGroupId());
      renderElement(TabStops.THREE, "artifactId", dependency.getArtifactId());
      renderElement(TabStops.THREE, "scope", dependency.getScope());
      renderElement(TabStops.THREE, "classifier", dependency.getClassifier());
      renderElement(TabStops.THREE, "type", dependency.getType());

      renderAvailableVersions(entry.getValue());
      endElement(TabStops.TWO, "dependency");
    }
    endElement(TabStops.ONE, "dependencies");
  }

  private boolean renderMagnitude(Updates av, Magnitude magnitude) {
    String magnitudeElement = magnitude.name().toLowerCase();
    String versionsTag = magnitudeElement + "s";

    Iterator<ArtifactVersion> it = av.getUpdates(magnitude);
    if (!it.hasNext()) {
      return false;
    }

    startElement(TabStops.THREE, versionsTag);
    do {
      ArtifactVersion version = it.next();
      renderElement(TabStops.FOUR, magnitudeElement, version.toString());
    } while (it.hasNext());
    endElement(TabStops.THREE, versionsTag);
    return true;
  }

  /**
   * Makes report file with given name in target directory.
   *
   * @throws MavenReportException if something went wrong
   * @param encoding
   */
  public void render(String encoding) throws MavenReportException {
    startElement(TabStops.ZERO, "DependencyUpdatesReport");
    renderSummary();
    renderDependencies();
    endElement(TabStops.ZERO, "DependencyUpdatesReport");

    PrintWriter pw;
    try {
      pw = new PrintWriter(reportFile, encoding);
      pw.print(sb.toString());
      pw.close();
    } catch (IOException e) {
      throw new MavenReportException("Cannot create xml report.", e);
    }
  }

  private enum TabStops {
    ZERO(0),
    ONE(1),
    TWO(2),
    THREE(3),
    FOUR(4);

    private final String spaces;

    TabStops(int stop) {
      spaces = new String(new char[stop * 2]).replace('\0', ' ');
    }

    @Override
    public String toString() {
      return spaces;
    }
  }
}

package org.honton.chas.updates;

import java.util.Iterator;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

/** The Updates for an artifact */
public class Updates {
  private final SortedSet<ArtifactVersion> versions;
  private final ArtifactVersion current;
  private final Artifact artifact;

  public Updates(Artifact artifact, Iterator<ArtifactVersion> updates) {
    versions = new TreeSet<>();
    while (updates.hasNext()) {
      versions.add(updates.next());
    }
    current = new DefaultArtifactVersion(artifact.getVersion());
    this.artifact = artifact;
  }

  private boolean getDiff(String magnitude, int update, int current) {
    int diff = update - current;
    if (diff > 0) {
      return true;
    }
    if (diff < 0) {
      throw new IllegalStateException(
          artifact
              + ": "
              + magnitude
              + " update version ("
              + update
              + ") less than current version("
              + current
              + ") ");
    }
    return false;
  }

  /** Get the current version of the dependent artifact
   * @return The current version
   */
  public ArtifactVersion getCurrentVersion() {
    return current;
  }

  /**
   * Get available updates for the dependent artifact, filtered by magnitude
   *
   * @param magnitude The magnitude of difference between the current version and the available
   *     version
   * @return Any updates available for the artifact with the given magnitude difference
   */
  public Iterator<ArtifactVersion> getUpdates(final Magnitude magnitude) {
    return new PredicateIterator<>(versions, version -> getMagnitude(version) == magnitude);
  }

  /**
   * Get the magnitude of difference between a given artifact update version and its current version
   *
   * @param version THe update version
   * @return The difference magnitude
   */
  public Magnitude getMagnitude(ArtifactVersion version) {
    if (getDiff("Major", version.getMajorVersion(), current.getMajorVersion())) {
      return Magnitude.Major;
    }

    if (getDiff("Minor", version.getMinorVersion(), current.getMinorVersion())) {
      return Magnitude.Minor;
    }

    if (getDiff("Incremental", version.getIncrementalVersion(), current.getIncrementalVersion())) {
      return Magnitude.Incremental;
    }

    if (getDiff("BuildNumber", version.getBuildNumber(), current.getBuildNumber())) {
      return Magnitude.SubIncremental;
    }
    return Magnitude.Equal;
  }

  /**
   * Get the Magnitude of difference between the current version and the next available update.
   *
   * @return The Magnitude
   */
  public Magnitude getSmallestDifference() {
    return versions.isEmpty() ? Magnitude.Equal : getMagnitude(versions.first());
  }

  /**
   * Get the next available update.
   *
   * @return The version of the next available update.
   */
  public ArtifactVersion getNextVersion() {
    return versions.isEmpty() ? null : versions.first();
  }
}

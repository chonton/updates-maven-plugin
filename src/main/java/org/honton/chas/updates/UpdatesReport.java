package org.honton.chas.updates;

import java.io.File;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataRetrievalException;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.OverConstrainedVersionException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;

/** Generate report of available updates */
@Mojo(
    name = "report",
    requiresDependencyResolution = ResolutionScope.RUNTIME,
    defaultPhase = LifecyclePhase.VERIFY)
public class UpdatesReport extends AbstractMavenReport {

  @Component private ArtifactFactory artifactFactory;
  @Component private ArtifactMetadataSource metadataSource;

  @Parameter(defaultValue = "${project.remoteArtifactRepositories}", readonly = true)
  private List remoteRepositories;

  @Parameter(defaultValue = "${localRepository}", readonly = true)
  private ArtifactRepository localRepository;

  /** Output report name */
  @Parameter(property = "updates.report", defaultValue = "dependency-updates-report.xml")
  private String reportName;

  /**
   * Regular expression of qualifiers that will be accepted. Any updates that have a qualifier and
   * do not match the expression will not be reported. This is to prevent a false report on
   * -SNAPSHOT, -RC, .alpha, .beta, etc.
   */
  @Parameter(property = "updates.qualifier", defaultValue = "(?i:Final|GA|Release)")
  private String qualifierRegex;

  private Pattern pattern;

  /** Number of threads to retrieve dependency updates */
  @Parameter(property = "updates.threads", defaultValue = "8")
  private int retrievalThreadCount;

  /** Skip generating report */
  @Parameter(property = "updates.skip")
  private boolean skip;

  @Override
  public String getDescription(Locale locale) {
    return "Details of the updated dependencies";
  }

  @Override
  public String getName(Locale locale) {
    return "Updates Report";
  }

  @Override
  public String getOutputName() {
    return reportName;
  }

  @Override
  protected void executeReport(Locale locale) throws MavenReportException {
    if (!skip) {
      try {
        pattern = Pattern.compile(qualifierRegex);
        new UpdatesRenderer(getReportFile(), retrieveUpdates()).render(getOutputEncoding());
      } catch (InvalidVersionSpecificationException | OverConstrainedVersionException e) {
        throw new MavenReportException(e.getMessage(), e);
      }
    }
  }

  /**
   * Get the report file
   *
   * @return The File for the report
   */
  File getReportFile() {
    File reportDir = getReportOutputDirectory();
    if (!reportDir.isAbsolute()) {
      reportDir = new File(getProject().getBuild().getDirectory(), reportDir.getPath());
    }
    if (!reportDir.exists()) {
      reportDir.mkdirs();
    }
    return new File(reportDir, getOutputName());
  }

  /**
   * Get all dependencies of the project
   *
   * @return The dependencies (as Artifacts)
   * @throws InvalidVersionSpecificationException
   */
  Set<Artifact> getDependencies() throws InvalidVersionSpecificationException {
    Set<Artifact> dependencies = new TreeSet<>();
    for (Dependency dependency : (List<Dependency>) getProject().getDependencies()) {
      dependencies.add(getArtifact(dependency));
    }
    return dependencies;
  }

  /**
   * Get the artifact view of a dependency.
   *
   * @param dependency A Dependency
   * @return The Artifact
   * @throws InvalidVersionSpecificationException
   */
  Artifact getArtifact(Dependency dependency) throws InvalidVersionSpecificationException {
    return artifactFactory.createDependencyArtifact(
        dependency.getGroupId(),
        dependency.getArtifactId(),
        VersionRange.createFromVersionSpec(dependency.getVersion()),
        dependency.getType(),
        dependency.getClassifier(),
        dependency.getScope());
  }

  /**
   * Retrieve all updates to the dependencies of the project.
   *
   * @return The updates for each dependency
   */
  SortedMap<Artifact, Updates> retrieveUpdates()
      throws OverConstrainedVersionException, InvalidVersionSpecificationException,
          MavenReportException {

    Set<Artifact> dependencies = getDependencies();
    ExecutorService executor = Executors.newFixedThreadPool(retrievalThreadCount);
    try {
      List<Future<Entry<Artifact, Updates>>> futures = new ArrayList<>(dependencies.size());
      for (Artifact dependency : dependencies) {
        futures.add(executor.submit(createRetrievalTask(dependency)));
      }

      SortedMap<Artifact, Updates> updates = new TreeMap<>();
      for (Future<Map.Entry<Artifact, Updates>> future : futures) {
        Map.Entry<Artifact, Updates> dav = future.get();
        updates.put(dav.getKey(), dav.getValue());
      }
      return updates;
    } catch (ExecutionException ee) {
      Throwable t = ee.getCause();
      if (t instanceof Exception) {
        throw new MavenReportException("Update retrieval was unsuccessful", (Exception) t);
      }
      if (t instanceof Error) {
        throw (Error) t;
      }
      throw new UndeclaredThrowableException(t);
    } catch (InterruptedException ie) {
      throw new MavenReportException("Update retrieval was interrupted", ie);
    } finally {
      executor.shutdownNow();
    }
  }

  List<ArtifactVersion> available(Artifact artifact) throws ArtifactMetadataRetrievalException {
    return metadataSource.retrieveAvailableVersions(artifact, localRepository, remoteRepositories);
  }

  /**
   * Create a retrieval task for an artifact
   *
   * @param artifact The artifact to retrieve
   * @return The Callable that will retrieve the Updates for the Artifact
   */
  Callable<Entry<Artifact, Updates>> createRetrievalTask(Artifact artifact)
      throws OverConstrainedVersionException {
    ArtifactVersion selectedVersion = artifact.getSelectedVersion();
    final Predicate<ArtifactVersion> predicate =
        artifactVersion -> {
          if (selectedVersion.compareTo(artifactVersion) >= 0) {
            getLog()
                .debug(
                    "Older version ignored " + artifact + ", ArtifactVersion: " + artifactVersion);
            return false;
          }
          // check qualifier for weird stuff
          String qualifier = artifactVersion.getQualifier();
          if (qualifier == null) {
            return true;
          }
          boolean matches = pattern.matcher(qualifier).matches();
          getLog()
              .debug(
                  "artifact: "
                      + artifact
                      + ", qualifier: "
                      + qualifier
                      + (matches ? ", matches" : ", does not match"));
          return matches;
        };

    return () ->
        new SimpleEntry(
            artifact,
            new Updates(artifact, new PredicateIterator<>(available(artifact), predicate)));
  }
}

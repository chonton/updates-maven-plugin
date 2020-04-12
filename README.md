# updates-maven-plugin

Report which dependencies of a project have updates.  This plugin provides a similar report to the 
[versions updates-report](https://www.mojohaus.org/versions-maven-plugin/dependency-updates-report-mojo.html).
This plugin provides simpler configuration to eliminate non-production updates in the report.  This
plugin will only produce an xml report of updates for the declared dependencies in a project.

## Goals
There is a single goal.  [report](https://chonton.github.io/updates-maven-plugin/0.1.0/report-mojo.html)
generates an [xml report](https://chonton.github.io/updates-maven-plugin/0.1.0/dependency-updates-report.xml)
of the updates available for each dependency. 

Mojo details at [plugin info](https://chonton.github.io/updates-maven-plugin/0.1.0/)

## Parameters
| Parameter | Property | Default | Description |
|:----------|:---------|:--------|:------------|
|localRepository|localRepository| |The local repositories to check for updates|
|outputDirectory|project.reporting.outputDirectory|site|The directory for the report|
|outputEncoding|outputEncoding|UTF-8|The encoding for the report|
|qualifierRegex|updates.qualifier|(?i:Release&vert;GA&vert;Final)|The regular expression to accept qualifiers|
|remoteArtifactRepositories|project.remoteArtifactRepositories| |The remote repositories to check for updates|
|reportName|updates.report|dependency-updates-report.xml|The report name|
|retrievalThreadCount|updates.threads|8|Number of threads to retrieve dependency updates|
|skip|updates.skip|false|Skip executing the plugin|

## Ignoring Non-production updates
You probably don't want to be informed of alpha, beta, snapshot, release candidates or other
non-production ready versions.  The ```qualifierRegex``` parameter allows you to specify which
version qualifiers will be reported as updates.  The default ```qualifierRegex``` specifies a
case-insensitive match of **Release**, **GA**, or **Final**.

## Requirements
- Maven 3.5 or later
- Java 1.8 or later

## Typical Use

```xml
  <build>
    <plugins>

      <plugin>
        <groupId>org.honton.chas</groupId>
        <artifactId>updates-maven-plugin</artifactId>
        <version>0.1.0</version>
        <executions>
          <execution>
            <goals>
              <goal>report</goal>
            </goals>
          </execution>
        </executions>
      </plugin>

    </plugins>
  </build>
```

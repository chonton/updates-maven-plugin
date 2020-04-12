package org.honton.chas.updates;

/**
 * Magnitude of update difference. Somewhat follows the SemVer semantics. Value indicate the
 * magnitude of difference between two versions. (Major).(Minor).(Incremental).(SubIncremental)
 */
public enum Magnitude {
  Major,
  Minor,
  Incremental,
  SubIncremental,
  Equal;
}

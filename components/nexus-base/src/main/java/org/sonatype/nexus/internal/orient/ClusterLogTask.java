/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.internal.orient;

import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;

import org.sonatype.nexus.orient.DatabaseInstance;
import org.sonatype.nexus.orient.DatabaseInstanceNames;
import org.sonatype.nexus.orient.DatabaseMaintenanceService;
import org.sonatype.nexus.scheduling.TaskSupport;

import com.google.common.collect.ImmutableList;
import com.orientechnologies.orient.core.metadata.schema.OClass;

import static java.util.stream.Collectors.joining;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.sonatype.nexus.logging.task.TaskLoggingMarkers.CLUSTER_LOG_ONLY;

/**
 * Task that logs cluster information
 *
 * @since 3.next
 */
@Named
public class ClusterLogTask
    extends TaskSupport
{
  public static final String SEPARATOR = "+--------------------------------+-----------------+\n";

  private final List<Provider<DatabaseInstance>> databases;

  private final Provider<DatabaseMaintenanceService> databaseMaintenanceServiceProvider;

  @Inject
  public ClusterLogTask(
      @Named(DatabaseInstanceNames.CONFIG) final Provider<DatabaseInstance> configDb,
      @Named(DatabaseInstanceNames.COMPONENT) final Provider<DatabaseInstance> componentDb,
      final Provider<DatabaseMaintenanceService> databaseMaintenanceServiceProvider)
  {
    super(false);
    databases = ImmutableList.of(checkNotNull(configDb), checkNotNull(componentDb));
    this.databaseMaintenanceServiceProvider = checkNotNull(databaseMaintenanceServiceProvider);
  }

  @Override
  protected Object execute() {
    if (log.isInfoEnabled(CLUSTER_LOG_ONLY)) {
      databases.stream().map(Provider::get).forEach(this::logTable);
      logHaStatus();
      logProfilerStatistics();
    }
    else {
      log.debug("Logging for cluster information is not enabled by logging level");
    }
    return null;
  }

  private void logTable(DatabaseInstance db) {  // NOSONAR
    StringBuilder table = new StringBuilder();
    table.append("\n")
        .append(SEPARATOR)
        .append(String.format("| %-48s |%n", db.getName() + " Database"))
        .append(SEPARATOR)
        .append("| Table Name                     | Count           |\n")
        .append(SEPARATOR);
    db.acquire().getMetadata().getSchema().getClasses().stream()
        .sorted(Comparator.comparing(OClass::getName, String.CASE_INSENSITIVE_ORDER))
        .map(c -> String.format("| %-30s | %-15d |%n", c.getName(), c.count()))
        .forEach(table::append);
    table.append(SEPARATOR);
    log.info(CLUSTER_LOG_ONLY, "\n{}", table);
  }

  private void logHaStatus() {
    log.info(CLUSTER_LOG_ONLY, databaseMaintenanceServiceProvider.get().fullServerStatus());
  }

  private void logProfilerStatistics() {
    String statisticsString = databaseMaintenanceServiceProvider.get().profilerStatistics().entrySet().stream()
        .map(e -> "  " + e.getKey() + ": " + e.getValue())
        .collect(joining("\n"));
    log.info(CLUSTER_LOG_ONLY, "Profiler statistics:\n" + statisticsString);
  }

  @Override
  public String getMessage() {
    return "Log cluster information";
  }
}

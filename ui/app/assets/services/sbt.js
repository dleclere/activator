/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  './sbt/app',
  './sbt/dependencies',
  './sbt/logs',
  './sbt/tasks',
  './sbt/events',
  './sbt/configuration',
  './sbt/tests'
],function(
  app,
  dependencies,
  logs,
  tasks,
  events,
  configuration,
  tests
) {

  return {
    app:            app,
    dependencies:   dependencies,
    logs:           logs,
    tasks:          tasks,
    events:         events,
    configuration:  configuration,
    tests:          tests
  };

});

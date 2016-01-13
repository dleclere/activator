/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'commons/websocket',
  './tasks',
  './events',
  './app'
], function(
  websocket,
  tasks,
  events,
  app
) {

  var logs = ko.observableArray([]);
  var stdout = ko.observableArray([]);

  // Websocket Handlers
  var logEvent = websocket.subscribe("type", "sbt");

  function filterDebug(m) {
    if (m.event.entry && m.event.entry.level)
      return m.event.entry.level !== "debug" || (app.settings.showLogDebug() || debug);
    else
      return true;
  }

  var countError = function(logs, m, which) {
    if (m.event.entry && m.event.entry.level && m.event.entry.level === "error") {
      // we get several error lines for each error, so we count each adjacent
      // block of lines as one count. Counting three errors as one is better than
      // counting one as three.
      if (logs().length === 0 || (logs()[logs().length - 1].event.entry.level !== "error")) {
        events.incrementCounters[which]();
      }
    }
  };

  /**
  Logs, by execution/task
  */
  logEvent.matchOnAttribute("subType", "TaskLogEvent")
    .filter(filterDebug)
    .each(function(m) {
      countError(logs, m, 'build');
      logs.push(m);
    });

  logEvent.matchOnAttribute("subType", "DetachedLogEvent")
    .filter(filterDebug)
    .each(function(m) {
      countError(logs, m, 'build');
      logs.push(m);
    });

  logEvent
    .matchOnAttribute("subType", "BackgroundJobLogEvent")
    .each(function(m) {
      countError(stdout, m, 'run');
      stdout.push(m);
    });

  return {
    logs: logs,
    stdout: stdout
  }

});

/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  "services/sbt",
  "main/plugins",
  "text!./tasks.html",
  "css!./tasks",
  "css!widgets/modules/modules"
], function(
  sbt,
  plugins,
  tpl
) {

  function scrollToBottom() {
    $(".logs")[0].scrollTop = 999999;
    State.memoLogsScroll('stick');
  }

  // Cache the dom Logs, for better performances
  // Not very elegant, but much, much, much more efficient.
  var logsView = ko.tpl("ul", {logEach: sbt.logs.logs, css: { 'show-debug': sbt.app.settings.showLogDebug }}, [
    ko.tpl("li", { attr: { 'data-bind': "text: event.entry.message, attr: { 'data-level': event.entry.level, 'data-type': event.entry.$type }"} }, [])
  ]);

  var State = {
    memoTaskScroll: ko.observable(),
    memoLogsScroll: ko.observable(),
    sbt: sbt,
    logsView: logsView,
    scrollToBottom: scrollToBottom,
    clear: function() {
      sbt.logs.logs.removeAll();
    },
    rerunTask: function(task, e) {
      e.preventDefault();
      e.stopPropagation();
      if (!task.finished()){
        sbt.tasks.kill(task);
      } else {
        sbt.tasks.requestExecution(task.command);
      }
    },
  };

  return {
    route: function(url, breadcrumb) {
    },

    render: function(){
      return ko.bindhtml(tpl, State);
    }
  }

});

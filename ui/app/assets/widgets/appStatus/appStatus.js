/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'services/sbt',
  'text!./appStatus.html',
  'css!./appStatus',
  'css!widgets/stickers/stickers'
], function(
  sbt,
  tpl
) {

  var off = sbt.tasks.applicationNotReady;

  function StatusButton(opts){
    var self = this;
    self.pending = opts.pending || ko.observable(false);
    self.working = opts.working || ko.observable(false);
    self.disabled = opts.disabled || ko.observable(false);
    self.click = function(e) {
      opts.click.call(self);
    };
    self.text = ko.computed(function() {
      if (self.pending()) {
        return opts.labels.working;
      } else if (self.working()) {
        return opts.labels.pending;
      } else {
        return opts.labels.inactive;
      }
    });
  }

  var onoff = new StatusButton({
    labels: {
      working: "Kill all tasks",
      pending: "Kill all tasks",
      inactive: "Kill all tasks"
    },
    click: function() {
      sbt.tasks.actions.kill();
    },
    disabled: off
  });

  var compile = new StatusButton({
    labels: {
      working: "Compiling",
      pending: "Pending...",
      inactive: "Compile"
    },
    click: function() {
      if (!this.working()){
        sbt.tasks.actions.compile();
      } else {
        sbt.tasks.actions.kill("compile");
      }
    },
    working: sbt.tasks.workingTasks.compile,
    pending: sbt.tasks.pendingTasks.compile,
    disabled: off
  });

  var run = new StatusButton({
    labels: {
      working: "Running",
      pending: "Pending...",
      inactive: "Run"
    },
    click: function() {
      if (!this.working()){
        sbt.tasks.actions.run();
      } else {
        sbt.tasks.actions.stopRun();
      }
    },
    working: sbt.tasks.workingTasks.run,
    pending: sbt.tasks.pendingTasks.run,
    disabled: off
  });

  var testing = new StatusButton({
    labels: {
      working: "Testing",
      pending: "Pending...",
      inactive: "Test"
    },
    click: function() {
      if (!this.working()){
        sbt.tasks.actions.test();
      } else {
        sbt.tasks.actions.kill("test");
      }
    },
    working: sbt.tasks.workingTasks.test,
    pending: sbt.tasks.pendingTasks.test,
    disabled: off
  });

  ko.bindingHandlers.appStatusButton = {
    init: function(element, valueAccessor) {
      var model = valueAccessor();
      ko.applyBindingsToNode(element, { css: {pending: model.pending, working: model.working, disabled: model.disabled},  click: model.click });
    }
  };

  sbt.tasks.taskCompleteEvent.subscribe(function(e) {
    var command;
    switch(e.command){
      case "clean":
      case "reload":
      case "compile":
        command = "compile";
        break;
      case "start":
      case "run":
        command = "run";
        break;
      case "test":
      case "testOnly":
        command = "test";
        break;
      default:
        command = "onoff";
        break;
    }
    $("#appStatus ."+command+" .animate").removeClass('animate');
    var el = $("#appStatus ."+command+" ."+(e.succeded?"success":"error"));
    setTimeout(function(){
      el.addClass('animate');
    },50);
  });

  var State = {
    onoff: onoff,
    compile: compile,
    run: run,
    testing: testing,
    whyDisabled: sbt.events.whyDisabled
  };

  return ko.bindhtml(tpl, State);

});

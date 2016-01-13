/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'main/plugins',
  'services/sbt',
  'text!./test.html',
  "widgets/layout/layout",
  'css!./test',
  "css!widgets/buttons/switch",
  "css!widgets/menu/menu",
  "css!widgets/buttons/select",
  "css!widgets/buttons/button",
  "css!widgets/modules/modules"
], function(
  plugins,
  sbt,
  tpl,
  layout
){

  var mainTestAction = function() {
    if (sbt.tasks.pendingTasks.test()){
      sbt.tasks.actions.kill("test");
    } else {
      sbt.tasks.actions.test();
    }
  }
  var mainTestName = ko.computed(function() {
    return sbt.tasks.pendingTasks.test()?"Stop":"Test";
  });


  var sbtExecCommand = function(cmd){
    sbt.tasks.requestExecution(cmd);
  }

  var State = {
    results: sbt.tasks.testResults,
    retestOnSuccessfulBuild: sbt.app.settings.retestOnSuccessfulBuild,
    sbtExecCommand: sbtExecCommand,
    mainTestAction: mainTestAction,
    mainTestName: mainTestName,
    sbt: sbt
  }

  return {
    render: function(url){
      layout.renderPlugin(ko.bindhtml(tpl, State))
    },

    route: function(url, breadcrumb){
      var all = [
        ['test/', "Test"]
      ];
      if(url.parameters[0]){
        breadcrumb(all);
      } else {
        breadcrumb(all);
      }
    }
  }
});

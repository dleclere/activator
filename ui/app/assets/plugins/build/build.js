/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  "main/plugins",
  "services/sbt",
  "text!./build.html",
  "widgets/layout/layout",
  "css!./build",
  "css!widgets/buttons/switch",
  "css!widgets/menu/menu",
  "css!widgets/buttons/button",
  "css!widgets/buttons/select",
  "css!widgets/lists/logs"
], function(
  plugins,
  sbt,
  tpl,
  layout
) {

  var subPlugin = ko.observable();
  var currentPlugin;

  var sbtExecCommand = function(cmd){
    sbt.tasks.requestExecution(cmd);
  }
  var setProject = function(project){
    sbt.app.currentProject(project);
  }
  var mainBuildAction = function() {
    if (sbt.tasks.pendingTasks.compile()){
      sbt.tasks.actions.kill("compile");
    } else {
      sbt.tasks.actions.compile();
    }
  }
  var mainBuildName = ko.computed(function() {
    return sbt.tasks.pendingTasks.compile()?"Stop":"Compile";
  });

  var subPlugins = {
    dependencies:   "Dependencies",
    configuration:  "Project configuration",
    console:        "Console",
    tasks:          "Tasks"
  }

  var State = {
    subPlugin: subPlugin,
    sbtExecCommand: sbtExecCommand,
    setProject: setProject,
    recompileOnChange: sbt.app.settings.recompileOnChange,
    sbt: sbt,
    mainBuildAction: mainBuildAction,
    mainBuildName:   mainBuildName
  }

  return {
    render: function(url) {
      subPlugin(null);
      currentPlugin = null;
      layout.renderPlugin(ko.bindhtml(tpl, State))
    },
    route: plugins.route('build', function(url, breadcrumb, plugin) {
      sbt.events.unreadBuildErrors().forEach(function(execution){
        execution.read(true);
      });
      if (currentPlugin !== plugin.id){
        currentPlugin = plugin.id;
        subPlugin(plugin.render());
      }
      if (url.parameters){
        breadcrumb([['build/', "Build"],['build/'+url.parameters[0], subPlugins[url.parameters[0]]]]);
      }
    }, "build/tasks")
  }

});

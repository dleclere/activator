/**
 * Copyright (C) 2014 Typesafe <http://typesafe.com/>
 */
define([
  './generator',
  'services/ajax',
  'widgets/openIn/openIn',
  'generated/dependencies'
], function (
  generator,
  fs,
  openIn,
  dependencies
) {

  var projectFile = ".idea";
  var pluginFileLocation = "/project/idea.sbt";
  var pluginFileContent = "// This plugin adds commands to generate IDE project files\n\n" +
    "addSbtPlugin(\"com.github.mpeltonen\" % \"sbt-idea\" % \""+dependencies.ideaVersion+"\")";
  var sbtCommand = "gen-idea";

  var isInstalled = ko.observable(false);
  function checkInstalled() {
    fs.exists(fs.absolute(projectFile), function(r) {
      isInstalled(r.location);
    });
  }
  checkInstalled();

  var generate = function(overrideExisting) {
    if (isInstalled() && !window.confirm("IntelliJ project already exists. Do you want to regenerate the project?")) return;
    else isInstalled(false);

    generator.startProcess(
      overrideExisting,
      projectFile,
      pluginFileLocation,
      pluginFileContent,
      isInstalled,
      sbtCommand);

    openIn.Idea(
      function() {
        generator.resetState("");
      },
      {
        logs: generator.logs,
        isInstalled: isInstalled,
        complete: generator.complete,
        scrollMemo: ko.observable(null)
      })
  };

  return {
    generate: generate,
    isInstalled: isInstalled,
    checkInstalled: checkInstalled
  };
});

/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  './tasks',
  'services/ajax',
  'generated/dependencies'
], function (
  tasks,
  ajax,
  dependencies
) {

  // plugin information
  var backgroundRunPluginFileLocation = "/project/sbt-ui.sbt";
  var backgroundRunPluginFileContent = "// This plugin represents functionality that is to be added to sbt in the future\n\n" +
    "addSbtPlugin(\"org.scala-sbt\" % \"sbt-core-next\" % \""+dependencies.sbtCoreNextVersion+"\")";
  var uiFileEchoSettings = "\n\nfork in run := true";

  // this file isn't required to exist, if it doesn't we should create
  var buildFileLocation = "/build.sbt";

  var playForkRunPluginFileLocation = "/project/play-fork-run.sbt";
  var playForkRunPluginFileContent = function (version) {
    return "// This plugin adds forked run capabilities to Play projects which is needed for Activator.\n\n" +
           "addSbtPlugin(\"com.typesafe.play\" % \"sbt-fork-run-plugin\" % \""+version+"\")";
  }

  var addedBackgroundFile = ko.observable(false);
  var addedForkInRun = ko.observable(false);
  var addedPlayForkRun = ko.observable(false);

  function checkFileContent(path, content, callback, appendTofile){
    return $.ajax({
      url: '/api/local/show',
      type: 'GET',
      dataType: 'text',
      data: { location: path }
    }).error(function(e) {
      tasks.clientReady(false);
      // File is not here / can't be opened
      ajax.create(path, false, content).then(callback);
    }).success(function(data) {
      // File is here
      var index = data.indexOf(content);
      if (index >= 0){
        callback();
      } else {
        ajax.save(path, appendTofile?data+content:content).success(callback);
      }
    })
  }

  // On start, we ensure that we have a sbt-ui.sbt file and the corresponding config in build.sbt
  checkFileContent(serverAppModel.location+backgroundRunPluginFileLocation, backgroundRunPluginFileContent, function() {
    addedBackgroundFile(true);
  });

  checkFileContent(serverAppModel.location+buildFileLocation, uiFileEchoSettings, function() {
    addedForkInRun(true);
  }, true);

  function checkPlayFileContent() {
    var callback = function () {
      addedPlayForkRun(true);
    };
    var path = serverAppModel.location+playForkRunPluginFileLocation;
    var ispa = tasks.isPlayApplication();
    if (ispa) {
      var playVersion = tasks.playVersion();
      var content = playForkRunPluginFileContent(playVersion);
      checkFileContent(path, content, callback);
    } else {
      callback();
    }
  }

  var prepReady = ko.computed(function() {
    checkPlayFileContent();
    // TODO this is completely broken because applicationReady is probably true to begin with,
    // then temporarily false AFTER we edit all the build files, but prepReady is going to
    // be briefly true before we restart (when we want it to be true only after).
    // I think we should replace applicationReady with checking that the needed tasks are
    // present in the build.
    return (tasks.applicationReady() && addedBackgroundFile() && addedForkInRun() && addedPlayForkRun());
  });

  function prepedAndReady(callback){
    if (prepReady()) callback();
    else {
      var subscription = prepReady.subscribe(function(ready) {
        if (ready){
          callback();
          subscription.dispose();
        }
      });
    }
  }

  return {
    prepedAndReady: prepedAndReady,
    prepReady:      prepReady
  };
});

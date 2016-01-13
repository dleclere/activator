/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'commons/utils',
  'commons/websocket',
  'services/sbt',
  'services/ajax'
], function (
  utils,
  websocket,
  sbt,
  ajax
) {

  // available states
  var idle = 1;
  var checkingProjectFile = 2;
  var checkingCommand = 3;
  var generatingFile = 4;
  var restartingSbt = 5;
  var runningCommand = 6;
  var generator = null;

  // Modal Window
  var logs = ko.observableArray([]);
  var complete = ko.observable(false);

  var Generator = utils.Class("Generator", {
    // all the stuff in here
    init: function (args) {
      this.overrideExisting = args.overrideExisting;
      this.projectFile = args.projectFile;
      this.pluginFileLocation = args.projectFileLocation;
      this.pluginFileContent = args.pluginFileContent;
      this.sbtCommand = args.sbtCommand;
      this.processTimeoutId = -1;
      this.currentState = idle;
      this.currentExecutionId = 0;
      this.isInstalled = args.isInstalled;
      this.subscription = websocket.subscribe('type', 'sbt');
      initializeSubscriptions(this.subscription);
    }
  });

  var initializeSubscriptions = function(s) {
    s.matchOnAttribute('subType','ExecutionSuccess').each(function(msg) { successExecutionHandler(msg); });
    s.matchOnAttribute('subType','ExecutionFailure').each(function(msg) { failureExecutionHandler(msg); });
    s.matchOnAttribute('subType','ClientOpened').each(function(msg) { clientOpenedHandler(msg); });
  }

  var setNewTimeout = function() {
    // Delete existing timeout
    clearTimeout(generator.processTimeoutId);
    // If the process takes more than n seconds we generate a message that it might be good to retry
    generator.processTimeoutId = setTimeout(function() { logs.push({message: "The process seems stuck. Press OK and please retry."})}, 60 * 1000);
  };

  var start = function() {
    // Reset any previous messages
    logs([]);
    logs.push({message: "Generating files may take a while. Sit back and relax."});
    setNewTimeout();
    checkProjectFile();
  };

  var checkProjectFile = function() {
    if (generator.overrideExisting === true) {
      checkCommand();
    } else {
      if (generator.currentState !== idle) {
        logs.push({message: "Cannot start process since is already in progress."});
      } else {
        generator.currentState = checkingProjectFile;
        logs.push({message: "Looking for existing project files."});

        // Look for a ".project" file in the home directory to see if there already is an existing Eclipse project
        ajax.exists(serverAppModel.location + "/" + generator.projectFile, function (status) {
          if (status === "success") {
            resetState("Required file(s) generated. Open your IDE and import project.");
          } else {
            // No project files found - continue the process
            logs.push({message: "No project file(s) found - continuing the process."});
            checkCommand();
          }
        });
      }
    }
  };

  var runSbtCommand = function() {
    return sbt.tasks.requestDeferredExecution(generator.sbtCommand);
  };

  var checkCommand = function() {
    generator.currentState = checkingCommand;
    logs.push({message: "Trying to run \'" + generator.sbtCommand + "\'..."});
    var result = runSbtCommand();
    result.done(function(data) {
      // Note: the result from sbt is asynchronous and the websocket subscription drives the process forward
      generator.currentExecutionId = data.result;
    }).fail(function() {
      resetState("Did not receive any response from server. Please try again.");
    });
  };

  var runCommand = function() {
    generator.currentState = runningCommand;
    logs.push({message: "Running the " + generator.sbtCommand + " command."});
    var result = runSbtCommand();
    result.done(function(data) {
      // Note: the result from sbt is asynchronous and the websocket subscription drives the process forward
      generator.currentExecutionId = data.result;
    }).fail(function() {
      resetState("Did not receive any response from server. Please try again.");
    });
  };

  var generateFile = function() {
    generator.currentState = generatingFile;
    var fileLocation = serverAppModel.location + generator.pluginFileLocation;
    logs.push({message: "Creating sbt IDE plugin file (" + fileLocation + ")."});
    ajax.create(fileLocation, false, generator.pluginFileContent).done(function () {
      // Adding the plugin file should trigger an automatic restart of sbt hence the state shift here
      // Note: the result from sbt is asynchronous and the stream subscription below will continue to drive the process
      generator.currentState = restartingSbt;
      setNewTimeout();
      logs.push({message: "Waiting for sbt to restart..."});
    }).fail(function(err) {
      resetState("Could not create sbt IDE plugin file. Please try again.");
    });
  };

  var resetState = function(msg) {
    if (generator) {
      clearInterval(generator.processTimeoutId);
      generator = null;
    }
    debug && console.log(msg);
    logs.push({message: msg});
  };

  var failureExecutionHandler = function(msg) {
    var executionId = msg.event.id;
    if (generator !== null && generator.currentExecutionId === executionId) {
      if (generator.currentState === checkingCommand) {
        logs.push({message: "\'" + generator.sbtCommand + "\' not available, trying to add a plugin to provide it."});
        generateFile();
      } else if (generator.currentState === runningCommand) {
        resetState("Could not run the \'" + generator.sbtCommand + "\' command. Please try again.");
      }
    }
  };

  var successExecutionHandler = function(msg) {
    var executionId = msg.event.id;
    if (generator !== null && generator.currentExecutionId === executionId) {
      if (generator.currentState === checkingCommand) {
        setNewTimeout();
        logs.push({message: "\'" + generator.sbtCommand + "\' command was available and has been run."});
        generator.isInstalled(true);
        resetState("Required files generated. Open your IDE and import project.");
      } else if (generator.currentState === runningCommand) {
        logs.push({message: "\'" + generator.sbtCommand + "\' command has been executed."});
        generator.isInstalled(true);
        resetState("Required files generated. Open your IDE and import project.");
      }
    }
  };

  var clientOpenedHandler = function(msg) {
    if (generator !== null && generator.currentState === restartingSbt) {
      setNewTimeout();
      runCommand();
    }
  };

  var startProcess = function (overrideExisting, projectFile, projectFileLocation, pluginFileContent, isInstalled, sbtCommand) {
    if (generator !== null) {
      resetState(); // should this throw an error instead?
    }

    generator = new Generator({
      overrideExisting: overrideExisting,
      projectFile: projectFile,
      projectFileLocation: projectFileLocation,
      pluginFileContent: pluginFileContent,
      isInstalled: isInstalled,
      sbtCommand: sbtCommand
    });

    start();
  };

  return {
    logs: logs,
    complete: complete,
    startProcess: startProcess,
    resetState: resetState
  };
});

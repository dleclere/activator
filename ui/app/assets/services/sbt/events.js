define([
  './app',
  './tasks',
  'services/ajax',
  'main/router',
  'commons/websocket'
],function(
  app,
  tasks,
  fs,
  router,
  websocket
) {

  /**
  Error counters
  */
  var errorCounters = {
    build: ko.observable(0),
    code:  ko.observable(0),
    run:   ko.observable(0),
    test:  ko.observable(0)
  }

  function incrementCounterContextually(context, counter) {
    return function() {
      // Only increment the counter if the context is not the current one
      if (router.current().meta && router.current().meta.path && router.current().meta.path.indexOf(context) !== 0) {
        counter(counter()+1);
      }
    }
  }

  var incrementCounters = {
    build: incrementCounterContextually("build/tasks", errorCounters.build),
    run: incrementCounterContextually("run/system", errorCounters.run)
  }

  // .. on route change
  window.addEventListener("hashchange", function(e) {
    var url = window.location.hash.slice(1);
    // Reset build counter when displaying the logs
    if (url.indexOf("build/tasks") === 0) {
      errorCounters.build(0);
    // Reset run oounter when displaying it
    } else if (url.indexOf("run/system") === 0) {
      errorCounters.run(0);
    }
  });


  /**
  Notification list
  */
  var notifications = ko.observableArray([]);

  /**
  Full text application status
  */
  var appStatus = ko.computed(function() {
    if(!websocket.isOpened()){
      return { id: "disconnected", label: "Not connected to Activator backend", url: "#build/tasks" }
    } else if(!tasks.clientReady()){
      return { id: "disconnected", label: "Waiting for sbt server to restart", url: "#build/tasks" }
    } else if(tasks.buildFailed()){
      return { id: "buildFailed", label: "Failed to load sbt configuration", url: "#build/tasks" }
    } else if(!tasks.buildReady()){
      return { id: "activity", label: "Loading sbt configuration", url: "#build/tasks" }
    } else if(tasks.compilationErrors().length){
      var errors = tasks.compilationErrors();
      // Go to first compile error (if position information exists)
      var url = "#build/tasks";
      if (errors[0].position) {
        url = "#code"+ fs.relative(errors[0].position.sourcePath)+":"+errors[0].position.line;
      }
      var label = " compilation error(s)";
      if (errors[0].severity === "Warn") {
        label = " compilation warning(s)";
      }
      return { id: "compilationError", label: errors.length+label, url: url }
    } else if(tasks.testErrors().length){
      return { id: "testFailed", label: tasks.testErrors().length+" test(s) failed", url: "#test" }
    } else if(tasks.workingTasks.compile()){
      return { id: "activity", label: "Compiling project", url: "#build/tasks" }
    } else if(tasks.applicationNotReady()){
      return { id: "activity", label: "Building project", url: "#build/tasks" }
    } else if(tasks.workingTasks.test()){
      return { id: "activity", label: "Testing project", url: "#build/test" }
    } else if(tasks.workingTasks.run()){
      return { id: "activity", label: "Running project", url: "#build/run" }
    } else if(tasks.workingTasks.current()) {
      var current = tasks.workingTasks.current();
      return { id: "activity", label: "Running '" + current.command + "'" , url: "#build/tasks" };
    } else {
      return { id: "ok", label: "No errors or activity right now", url: "#build/tasks" };
    }
  });

  var whyDisabled = ko.computed(function() {
    return tasks.applicationNotReady()?appStatus().label:'';
  });


  /**
  Notification object constructor
  */
  function notify(execution) {

    if (!execution.succeeded()){
      if ((execution.commandId === "run") && router.current().id !== "run"){
        incrementCounters.run();
        notifications.unshift(new Notification("Runtime error", "#run/", "run", execution));
      } else if (execution.commandId === "test"){
        // Only show notification if we don't see the result
        if (router.current().id !== "test") {
          notifications.unshift(new Notification("Test failed", "#test/results", "test", execution));
        }
      } else if (router.current().id !== "build"){
        if (execution.compilationErrors().length && execution.compilationErrors()[0].position){
          var url = "#code"+ fs.relative(execution.compilationErrors()[0].position.sourcePath)+":"+execution.compilationErrors()[0].position.line;
          notifications.unshift(new Notification("Compilation error", url, "code", execution));
        } else {
          incrementCounters.build();
          notifications.unshift(new Notification("Build error", "#build/tasks/"+execution.executionId, "build", execution));
        }
      } else {
        notifications.unshift(new Notification("Unknown error", "#build/tasks/"+execution.executionId, "unknown", execution));
      }
    }
  }

  var readNotifications = ko.observableArray([]);

  var unreadBuildErrors = ko.computed(function() {
    return tasks.executions().filter(function(execution) {
      return execution.finished() && !execution.succeeded() && readNotifications().indexOf(execution.executionId);
    });
  });

  function Notification(text, link, type, execution) {
    this.text = text;
    this.link = link;
    this.type = type;
    this.execution = execution;
    this.read = ko.observable(false);
  }

  /**
  Update counters, run auto-commands (eg: test on compile)
  */
  tasks.ProcessedExecutionsStream.each(function(execution) {
    // Update Tests counters
    // TODO errorCounters.test can just be a ko.computed from tasks.testResults
    if (execution.testResults().length > 0){
      errorCounters.test(execution.testResults().filter(function(t) {
        return t.outcome === "failed";
      }).length);
    }

    // Update Compile/Code counter
    // TODO errorCounters.code can just be a ko.computed from tasks.compilationErrors
    if (execution.changedCompileResult) {
      errorCounters.code(execution.compilationErrors().filter(function(m) {
        return m.severity === "Error" || m.severity === "Warn";
      }).length);
    }

    // Run auto-commands
    if (execution.succeeded() && execution.command === "compile") {
      if (app.settings.rerunOnBuild() && tasks.applicationReady()){
        debug && console.log("app.rerunOnBuild is on: Requesting 'run' task");
        tasks.actions.kill();
        tasks.actions.run();
      }
      if (app.settings.retestOnSuccessfulBuild()){
        debug && console.log("app.retestOnSuccessfulBuild is on: Requesting 'test' task")
        tasks.actions.test();
      }
    }

  });

  websocket.subscribe({ type:'sbt', subType:'ProjectFilesChanged' })
    .each(function() {
      if (app.settings.recompileOnChange()){
        debug && console.log("app.rerunOnBuild is on: Requesting 'compile' task")
        tasks.actions.compile();
      }
    });

  return {
    errorCounters:      errorCounters,
    incrementCounters:  incrementCounters,
    notifications:      notifications,
    unreadBuildErrors:  unreadBuildErrors,
    notify:             notify,
    appStatus:          appStatus,
    whyDisabled:        whyDisabled
  }

})

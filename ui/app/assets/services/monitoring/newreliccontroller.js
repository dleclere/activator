/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'commons/websocket',
  'commons/settings',
  './monitoringSolutions'
], function(
  websocket,
  settings,
  monitoringSolutions
){

  var licenseKey = settings.observable("newrelic.licenseKey", "");
  var isProjectEnabled = ko.observable(false);
  var available = ko.observable(false);
  var supportedJavaVersion = ko.observable({result:true, version:"Unknown"});
  var validKey = /^([0-9a-z]{40})$/i;

  var send = function (msg){
    websocket.send(msg);
  };

  var nrMessage = function (type) {
    return { request: 'NewRelicRequest', type: type };
  };

  var nrMessageWith = function (type,attributes) {
    return jQuery.extend(nrMessage(type), attributes);
  };

  var checkIsProjectEnabled = function () {
    send(nrMessage("isProjectEnabled"));
  };

  var checkIsSupportedJavaVersion = function () {
    send(nrMessage("isSupportedJavaVersion"));
  };

  var checkAvailable = function () {
    send(nrMessage("available"));
  };

  var enableProject = function () {
    send(nrMessageWith("enable", {key: licenseKey(), name: serverAppModel.name}));
  };

  var setObserveProvision = function(callback) {
    monitoringSolutions.provisioningProgress.set(callback);
    send(nrMessage("provision"))
  };

  var unsetObserveProvision = function() {
    monitoringSolutions.provisioningProgress.reset();
  };

  var licenseKeySaved = ko.computed(function() {
    var key = licenseKey();
    return validKey.test(key);
  });

  var stream = monitoringSolutions.stream.matchOnAttribute('subtype', 'newrelic');

  stream.map(function (response) {
    var event = response.event;
    if (event.type === "availableResponse") {
      debug && console.log("setting available to: " + event.result);
      available(event.result);
    }
    if (event.type === "provisioned") {
      debug && console.log("New Relic provisioned");
      send(nrMessage("available"));
    }
    if (event.type === "isProjectEnabledResponse") {
      debug && console.log("Setting isProjectEnabled to: " + event.result);
      isProjectEnabled(event.result);
      if (event.result) {
        monitoringSolutions.addNewRelic();
      }
    }
    if (event.type === "isSupportedJavaVersionResult") {
      debug && console.log("Setting isSupportedJavaVersionResult to: " + event.result);
      supportedJavaVersion({result: event.result, version: event.version});
    }
    if (event.type === "projectEnabled") {
      debug && console.log("Project enabled for New Relic");
      checkIsProjectEnabled();
      generateFiles();
    }
  });

  var generateFiles = function () {
    send(nrMessageWith("generateFiles", { location: serverAppModel.location, info: "" }));
  };

  // Initial request
  var init = function () {
    debug && console.log("Making initial request to check NR availability");
    checkAvailable();
    checkIsSupportedJavaVersion();
    checkIsProjectEnabled();
  };

  init();

  return {
    licenseKeySaved: licenseKeySaved,
    available: available,
    licenseKey: licenseKey,
    validKey: validKey,
    setObserveProvision: setObserveProvision,
    unsetObserveProvision: unsetObserveProvision,
    enableProject: enableProject,
    isProjectEnabled: isProjectEnabled
  };
});

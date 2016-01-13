/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  "main/plugins",
  "services/monitoring/newreliccontroller",
  "services/sbt",
  "../monitoringInstaller",
  "text!./newrelic.html",
  "css!./newrelic",
  "css!widgets/modules/modules",
  "css!widgets/lists/logs"
], function(
  plugins,
  newrelic,
  sbt,
  monitoringInstaller,
  tpl
  ) {

  var downloadDescriptions = {
    'downloadComplete': 'Download Complete',
    'validating': 'Validating',
    'extracting': 'Extracting',
    'complete': 'Complete'
  };

  var selectedTab = ko.observable("notice");
  var tempLicenseKey = ko.observable(newrelic.licenseKey() || "");

  var isProjectEnabled = ko.computed(function () {
    return newrelic.isProjectEnabled();
  });

  var downloading = ko.observable("");
  var error = ko.observable();

  var needProvision = ko.computed(function() {
    return !newrelic.available() || !newrelic.licenseKey();
  });

  var provisionObserver = function(m) {
    var event = m.event;
    var message = "";
    if (event.type === "provisioningError") {
      message = "Error provisioning New Relic: " + event.message
      error(message);
    } else if (event.type === "downloading") {
      message = "Downloading: " + event.url;
    } else if (event.type === "progress") {
      message = "";
      if (event.percent) {
        message = event.percent.toFixed(0) + "%";
      } else {
        message = event.bytes + " bytes";
      }
      downloading("Progress: " + message);
    } else {
      message = downloadDescriptions[event.type] || "UNKNOWN STATE";
    }

    downloading(message);

    if (event.type === "complete" || event.type === "provisioningError") {
      newrelic.unsetObserveProvision();
    }
  };

  var provisionNewRelic = function () {
    error("");
    newrelic.setObserveProvision(provisionObserver);
  };

  var saveLicenseKey = function () {
    if (!newrelic.validKey.test(tempLicenseKey())) {
      error("Invalid license key (must be 40 characters long and can only contain A-Z and 0-9).");
    } else {
      error("");
      newrelic.licenseKey(tempLicenseKey());
    }
  };

  var resetLicenseKey = function () {
    tempLicenseKey("");
    newrelic.licenseKey(null);
  };

  var enableNewRelic = function () {
    sbt.tasks.actions.kill();
    newrelic.enableProject();
    monitoringInstaller({
      provider: "New Relic",
      addingFile: "project/sbt-nr.sbt",
      addedFile: newrelic.available,
      prepReady: sbt.tasks.prepReady
    });
  };

  var State = {
    needProvision: needProvision,
    provisionNewRelic: provisionNewRelic,
    downloading: downloading,
    tempLicenseKey: tempLicenseKey,
    saveLicenseKey: saveLicenseKey,
    resetLicenseKey: resetLicenseKey,
    error: error,
    selectedTab: selectedTab,
    enableNewRelic: enableNewRelic,
    isProjectEnabled: isProjectEnabled,
    available: newrelic.available,
    licenseKey: newrelic.licenseKey
  };

  return {
    render: function(){
      return ko.bindhtml(tpl, State);
    }
  }
});

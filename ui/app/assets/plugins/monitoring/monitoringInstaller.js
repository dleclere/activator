/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'services/sbt/configuration',
  'widgets/modals/modals',
  'text!./monitoringInstaller.html'
],function(
  configuration,
  modals,
  tpl
){

  return function(state) {
    modals.show({
      title: "Configuring "+state.provider+"...",
      text: "Activator is adding "+state.provider+" to your project. This might take a minute.",
      body: ko.bindhtml(tpl, state),
      cancel: "close"
    });

    // FIXME : this does not work for AD/NR (we cannot check against "echo" - must use "newrelic" instead)
    configuration.prepedAndReady(function() {
      modals.hideModal();
    });
  };
});

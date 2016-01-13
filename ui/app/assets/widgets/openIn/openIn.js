/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define([
  'widgets/modals/modals',
  'text!./openInEclipse.html',
  'text!./openInIdea.html',
  'css!./openIn'
],function(
  modals,
  openInEclipse,
  openInIdea
){

  var OpenInEclipse = function(callback, state) {
    modals.show({
      title: "Generating Eclipse Files",
      body: ko.bindhtml(openInEclipse, state),
      okEnabled: state.isInstalled,
      callback: callback,
      ok: "OK"
    });
  };

  var OpenInIdea = function(callback, state) {
    modals.show({
      title: "Generating IntelliJ IDEA Files",
      body: ko.bindhtml(openInIdea, state),
      okEnabled: state.isInstalled,
      callback: callback,
      ok: "OK"
    });
  };

  var CloseModalWindow = function () {
    modals.hideModal();
  };

  return {
    Eclipse : OpenInEclipse,
    Idea : OpenInIdea,
    CloseModalWindow: CloseModalWindow
  };
});

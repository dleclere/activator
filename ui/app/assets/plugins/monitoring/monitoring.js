/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'main/plugins',
  'services/sbt',
  'text!./monitoring.html',
  "widgets/layout/layout",
  'css!./monitoring',
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

  var provider = ko.observable();

  var State = {
    provider: provider
  };

  // Subplugins titles
  var subPlugins = {
    newrelic:         "New Relic"
  };

  return {
    render: function(url){
      layout.renderPlugin(ko.bindhtml(tpl, State));
    },
    route: plugins.route('monitoring', function(url, breadcrumb, plugin) {
      if (url.parameters){
        breadcrumb([['monitoring/', "Monitor"],['monitoring/'+url.parameters[0], subPlugins[url.parameters[0]]]]);
      }
      provider(plugin.render());
    }, "monitoring")
  }
});

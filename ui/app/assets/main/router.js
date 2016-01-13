/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define(function() {

  var current    = ko.observable({});
  var breadcrumb = ko.observable([]);

  window.addEventListener("hashchange", function(e) {
    load(window.location.hash);
  });

  var load = function(url) {
    var metaInfo = parseUrl(url);

    // Structure of plugins are: 'plugins' + pluginName + '/' + pluginName
    // e.g 'plugins/tutorial/tutorial'
    require([metaInfo.pluginUrl], function(plugin) {
      plugin.id = plugin.id || metaInfo.plugin;
      plugin.meta = metaInfo;

      // if the current plugin is different from the new then we render the new plugin
      if (current().id !== metaInfo.plugin){
        // call the after lifecycle function, if any, on the plugin being switched out
        if (typeof current().afterRender === 'function') current().afterRender();
        // call the before lifecycle function, if any, on the plugin begin switched in
        if (typeof plugin.beforeRender === 'function') plugin.beforeRender();
        // render the new plugin
        $("#main").empty().append(plugin.render(metaInfo));
        current(plugin);
      }

      !!plugin.route && plugin.route(metaInfo, breadcrumb);
    }, function(){
      // TODO : send back a 404
    });
  }

  var parseUrl = function(url) {
    if (!url || url === "#") url = "welcome"; // Default plugin
    if (url[0] === "#") url = url.slice(1); // Remove extra hash
    var plugin = url.split("/")[0]; // Divide the path in sections
    return {
      path: url,
      plugin: plugin,
      pluginUrl: "plugins/" + plugin + "/" + plugin,
      parameters: url.split("/").slice(1)
    }
  }

  // This will redirect without adding a new state in browser history
  var redirect = function(hash) {
    if (history.replaceState !== null) {
      return history.replaceState(null, null, '#' + hash);
    }
  }

  return {
    current: current,
    load: load,
    breadcrumb: breadcrumb,
    redirect: redirect
  }

});

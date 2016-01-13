/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define(function() {

  var all = {};

  return {
    observable: function(label, def) {
      if (!all[label]) {
        var stored;
        if (def === null && def === undefined) throw "Default value can't be null: "+label;
        try {
          stored = JSON.parse(window.localStorage.getItem(label));
        } catch (e) {
          // Remove localstorage item if can't parse it
          localStorage.removeItem(label);
        } finally {
          var value = stored !== null && stored !== undefined ? stored : def;
          all[label] = ko.observable(value);
          debug && console.debug("[SETTINGS]:", label, value);
          all[label].subscribe(function(newValue) {
            window.localStorage[label] = JSON.stringify(newValue);
          });
          return all[label];
        }
      } else {
        throw "Settings observable should be declared only once.";
      }
    }
  };

});

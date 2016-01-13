/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define([
  'services/sbt',
  'services/ajax'
], function(
  sbt,
  fs
) {

  // options is the observable where we put the results
  var combinedSearch = function(keywords, options) {
    debug && console.log("starting search on " + keywords);
    return $.when(fs.search(keywords), sbt.tasks.deferredPossibleAutoCompletions(keywords))
      .then(function(searchValues, sbtCompletions) {
        var filtered = sbtCompletions.filter(function (el) {
          return !endsWith(el.title, ":");
        }).filter(function (el) {
          return !endsWith(el.title, "*");
        });

        filtered.sort(function(a,b) {
          var aa = a.title.toLowerCase(), bb = b.title.toLowerCase();
          if (aa < bb)
            return -1;
          else if (bb < aa)
            return 1;
          else
            return 0;
        });

        var result = [];
        if (filtered.length)
          result = result.concat([{"heading": "Tasks (select to execute)"}],filtered);
        if (searchValues.length)
          result = result.concat([{"heading": "Files (select to open)"}],searchValues);
        options(result);
      });
  }

  var endsWith = function(str, suffix) {
    return str.indexOf(suffix, str.length - suffix.length) !== -1;
  };

  return {
    combinedSearch: combinedSearch
  }
});

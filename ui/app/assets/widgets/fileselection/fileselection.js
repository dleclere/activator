/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define([
  'commons/utils',
  'text!./fileselection.html',
  'css!./fileselection.css'
], function(
  utils,
  tpl,
  css
) {

  function browse(location) {
    return $.ajax({
      url: '/api/local/browse',
      type: 'GET',
      dataType: 'json',
      data: {
        location: location
      }
    });
  }

  function browseRoots() {
    return $.ajax({
      url: '/api/local/browseRoots',
      type: 'GET',
      dataType: 'json'
    });
  }

  // File model...
  function File(config) {
    var self = this;
    self.name = config.name || config.humanLocation;
    self.location = config.location;
    self.humanLocation = config.humanLocation;
    self.isDirectory = config.isDirectory;
    self.isFile = !config.isDirectory;
    self.highlighted = ko.observable(false);
    self.cancelable = config.cancelable || false;
  }

  // Function for filtering...
  function fileIsHighlighted(file) {
    return file.highlighted();
  }
  function noop() {}

  var FileSelection = utils.Class({
    init: function(config) {
      var cfg = config || {};
      var self = this;
      // TODO - Allow context...
      self.selectText = config.selectText || 'Select this File/Directory';
      self.onSelect = config.onSelect || noop;
      self.onCancel = config.onCancel || noop;
      self.showFiles = ko.observable(cfg.showFiles || false);
      self.shownDirectory = ko.observable(cfg.initialDir || '');
      self.currentFiles = ko.observableArray([]);
      self.currentHighlight = ko.computed(function() {
        return $.grep(self.currentFiles(), fileIsHighlighted)[0];
      });
      self.hasCurrentHighlight = ko.computed(function() {
        if(self.currentHighlight()) {
          return true;
        }
        return false;
      });
      self.currentViewFiles = ko.computed(function() {
        var showFiles = self.showFiles();
        return $.grep(self.currentFiles(), function(file){
          if(file.isDirectory || showFiles) {
            return true;
          }
          return false;
        });
      });
      self.title = ko.observable(cfg.title || "Select a file")
      if(cfg.initialDir) {
        this.load(cfg.initialDir);
      } else {
        this.loadRoots();
      }
    },
    render: function(element) {
      var self = this;
      return ko.bindhtml(tpl, self);
    },
    clickDir: function(context,event){
      event.preventDefault();
      var self = this;
      if(context.location) {
        self.load(context.location);
      } else {
        // TODO - Only on windows.
        self.loadRoots();
      }
      return false;
    },
    chooseCurrent: function() {
      var self = this;
      self.onSelect(self.shownDirectory());
    },
    gotoParent: function() {
      var self = this;
      if (window.separator === "/") {
        // Unix
        self.load("/" + self.shownDirectory().split("/").slice(1,-1).join("/"));
      }
      else {
        // Windows
        // assumes single char drive letters
        if (self.shownDirectory().length === 3) {
          // C:\ -> Drive listing
          self.loadRoots();
        }
        else if (self.shownDirectory().split("\\").length === 2) {
          // C:\Users -> C:\
          self.load(self.shownDirectory().substr(0, 3))
        }
        else {
          // C:\Users\foo -> C:\Users
          self.load(self.shownDirectory().substr(0, self.shownDirectory().lastIndexOf("\\")));
        }
      }
    },
    highlight: function(file) {
      $.each($.grep(this.currentFiles(), fileIsHighlighted), function(idx, item) {
        item.highlighted(false);
      });
      file.highlighted(true);
    },
    loadRoots: function(dir) {
      var self = this;
      browseRoots().done(function(values) {
        self.currentFiles($.map(values, function(config) {
          return new File(config);
        }));
      }).error(function() {
        window.alert('Failed to load file system roots.');
      });
    },
    load: function(dir) {
      var self = this;
      browse(dir).done(function(values) {
        self.shownDirectory(values.humanLocation);
        var fileConfigs = [];
        fileConfigs.push.apply(fileConfigs, values.children || []);
        fileConfigs.sort(function(fileConfig1, fileConfig2) {
          if (fileConfig1.name.toLowerCase() < fileConfig2.name.toLowerCase())
            return -1;
          if (fileConfig1.name.toLowerCase() > fileConfig2.name.toLowerCase())
            return 1;
          return 0;
        });
        self.currentFiles($.map(fileConfigs, function(config) {
          return new File(config);
        }));
      }).error(function(e) {
        window.alert(e.responseText);
      });
    },
    select: function() {
      var currentFile = this.currentHighlight();
      if(currentFile) {
        this.onSelect(currentFile.location);
      } else {
        this.onSelect(this.shownDirectory());
      }
    },
    cancel: function() {
      this.onCancel();
    }
  });

  return FileSelection;

});

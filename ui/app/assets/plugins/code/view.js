/*
 Copyright (C) 2013 Typesafe, Inc <http://typesafe.com>
 */
define([
  "./imageView",
  "./codeView",
  'commons/utils',
  'commons/widget',
  "text!./viewWrapper.html",
  "text!./viewDefault.html"
], function(
  ImageView,
  CodeView,
  utils,
  Widget,
  viewOuter,
  defaultTemplate
){

  function open(location) {
    return $.ajax({
      url: '/api/local/open',
      type: 'GET',
      data: {
        location: location
      }
    });
  }

  function deleteFile(location) {
    return $.ajax({
      url: '/api/local/delete',
      type: 'PUT',
      data: {
        location: location
      }
    });
  }

  // Default view for when we don't know which other to use.
  var DefaultView = utils.Class(Widget, {
    id: 'code-default-view',
    template: defaultTemplate,
    init: function(args) {
      var self = this;
      self.file = args.file;
      self.filename = ko.computed(function() {
        return self.file().location;
      });
    },
    afterRender: function(a,b,c){
      //debug && console.log('abc', a,b,c)
    },
    scrollToLine: function(line) {
    }
  });

  // Fetch utility
  var FileBrowser = utils.Class(Widget, {
    id: 'file-browser-widget',
    template: viewOuter,
    init: function(args) {
      var self = this;
      // TODO - Detect bad url?
      self.file = args.file;

      self.subView = ko.computed(function() {
        var file = self.file()
        if(file && file.type() == 'code') {
          return new CodeView({ file: self.file });
        } else if(file && file.type() == 'image') {
          return new ImageView({ file: self.file});
        }
        return new DefaultView(args);
      });
      self.canSwitchTheme = ko.computed(function() {
        var sub = self.subView();
        return 'switchTheme' in sub;
      });
      self.canSetFontSize = ko.computed(function() {
        var sub = self.subView();
        return 'setFontSize' in sub;
      });
      self.title = ko.computed(function() {
        return self.file().relative();
      });
      self.isFile = ko.computed(function() {
        return !self.file().isDirectory();
      });
      self.readableFileSize = ko.computed(function() {
        var size = self.file().size();
        if(size < 1024) {
          return size.toFixed(2) + ' b';
        }
        size /= 1024.0;
        if(size < 1024) {
          return size.toFixed(2) + ' Kb';
        }
        size /= 1024.0;
        if(size < 1024) {
          return size.toFixed(2) + 'Mb';
        }
        size /= 1024.0;
        return size.toFixed(2) + 'Gb';
      });
      self.fileKindName = ko.computed(function() {
        if(self.file().type() == 'code') {
          return self.subView().highlight;
          // TODO - Add number of lines?
        }
        return self.file().type();
      });
      self.fileStats = ko.computed(function() {
        return self.fileKindName() + ' / ' + self.readableFileSize();
      });
    },
    openInSystemEditor: function() {
      var self = this;
      var loc =self.file().location;
      open(loc).success(function() {}).error(function(err) {
        debug && console.log('Failed to open file in browser: ', err)
        alert('Failed to open file.  This may be unsupported by your system.');
      });
    },
    scrollToLine: function(line) {
      this.subView().scrollToLine(line);
    },
    deleteFile: function() {
      var self = this;
      var result = window.confirm('Delete "' + self.file().name() + '"?');
      if (result) {
        deleteFile(self.file().location).done(function() {
          debug && console.log("deleted OK");
          self.file().reloadParent();
        }).fail(function(err) {
          debug && console.log("Failed to delete file: ", err);
          alert('Failed to delete file: ' + err.responseText);
        });
      }
    },
    saveBeforeSwitchFiles: function(onDone, onCancel) {
      var self = this;
      if (self.file().isContentsDirty()) {
        var result = window.confirm('Do you want to close "' + self.file().name() + '" without saving?');
        if (result) {
          if (onDone) onDone();
        } else {
          if (onCancel) onCancel();
        }
      } else {
        if (onDone) onDone();
      }
    },
    onSwitchTheme: function(data, event) {
      var theme = event.target.innerText || event.target.text;
      this.subView().switchTheme(theme);
    },
    onSetFontSize: function(data, event) {
      this.subView().setFontSize(event.target.dataset.fontSize);
    }
  });

  return FileBrowser;
});

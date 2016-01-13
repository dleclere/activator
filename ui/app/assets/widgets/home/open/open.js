/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  'commons/websocket',
  'services/ajax',
  'widgets/fileselection/fileselection',
  'text!./open.html',
  'css!./open'
], function(
  websocket,
  fs,
  FileSelection,
  tpl
) {

  var State = function(){
    var self = this;

    self.fs = new FileSelection({
      selectText: 'Open this Project',
      initialDir: window.baseFolder,
      onCancel: function() {
        self.toggleAppBrowser();
      },
      onSelect: function(file) {
        // TODO - Grey out the app while we wait for response.
        websocket.send({
          request: 'OpenExistingApplication',
          location: file
        });
        $('#working, #open, #new').toggle();
      }
    });

    self.toggleAppBrowser = function() {
      $('#openAppForm, #openAppLocationBrowser').toggle();
    };
    self.clickOpenButton = function() {
      $('#openButton').toggleClass("opened");
      self.toggleAppBrowser();
    }

    self.deleteApp = function(app,e) {
      e.preventDefault();
      e.stopPropagation();
      if (window.confirm("Remove project from list?")){
        fs.deleteApp(app).success(function() {
          if (window.confirm("Do you want to permanently delete all files in "+app.location+"?")){
            fs.delete(app.location, true).success(function() {
              $(e.target).parent("li.recentApp").remove();
            });
          } else {
            $(e.target).parent("li.recentApp").remove();
          }
        });
      }
    }

  }

  return {
    render: function() {
      return ko.bindhtml(tpl, new State());
    }
  }

})

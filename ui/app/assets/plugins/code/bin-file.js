/*
 Copyright (C) 2014 Typesafe, Inc <http://typesafe.com>
 */
define([
  "services/ajax",
  'ace/ace'
],function(
  fs,
  ace
){


  var fileState = function(doc, type){
    var self = this;
    self.isText = false;
    self.title = doc.title;
    self.location = doc.location;
    self.active = ko.observable(false); // == displayed document
    self.type = type;
    self.fileLoadUrl = '/api/local/show?location='+doc.location;
    self.edited = ko.observable(false);

    // Right click on the tab
    self.contextmenu = {
      'no option': function(){}
    }
  }

  return fileState;

});
